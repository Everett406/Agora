package com.newoether.agora.viewmodel

import com.newoether.agora.model.CitationPolicy
import com.newoether.agora.model.CitationRecord
import com.newoether.agora.model.MessageSegment
import com.newoether.agora.model.toCitationRecord
import com.newoether.agora.model.toMessageSegment

/**
 * One generation's ordered segment buffer.
 *
 * Segments are the durable streaming format: an answer run, collapsible thinking, provider
 * citations, and image transcriptions are appended in emission order. The overlay owns only that
 * ordering plus live edits; it never executes work or decides a Run transition.
 */
internal class GenerationToolOverlay(
    private val providerName: String,
) {
    private val segments = mutableListOf(MessageSegment(type = "answer"))
    private val citations = mutableListOf<CitationRecord>()

    val size: Int
        get() = segments.size

    fun snapshot(): List<MessageSegment> =
        segments.toList() + citations.map { it.toMessageSegment() }

    fun contentSnapshot(): List<MessageSegment> = segments.toList()

    fun replaceAll(replacement: List<MessageSegment>) {
        segments.clear()
        citations.clear()
        replacement.forEach { segment ->
            if (segment.type == "citation") {
                segment.toCitationRecord()?.let(::upsertCitation)
            } else {
                segments += segment
            }
        }
    }

    fun prependAll(prefix: List<MessageSegment>) {
        if (prefix.isEmpty()) return
        val contentPrefix = prefix.filter { it.type != "citation" }
        prefix.mapNotNull { it.toCitationRecord() }.forEach(::upsertCitation)
        if (contentPrefix.isEmpty()) return
        segments.addAll(0, contentPrefix)
    }

    fun append(segment: MessageSegment) {
        if (segment.type == "citation") {
            segment.toCitationRecord()?.let(::upsertCitation)
        } else {
            appendMergedSegment(segments, segment)
        }
    }

    /**
     * Appends a thinking-style transcription segment (the same type the main transcription
     * stage streams) and returns its index for live updates. Consecutive calls produce
     * consecutive segments, matching the main flow's ordinal labeling.
     */
    fun appendTranscriptionSegment(content: String): Int {
        val index = segments.size
        segments += MessageSegment(type = "transcription", content = content)
        return index
    }

    fun updateTranscriptionSegment(index: Int, content: String) {
        if (index !in segments.indices) return
        segments[index] = segments[index].copy(content = content)
    }

    fun updateLastThoughtMetadata(
        signature: String?,
        signatureProvider: String?,
    ): Boolean {
        val index = segments.indexOfLast { segment -> segment.type == "thought" }
        if (index < 0) return false
        if (signature != null) {
            segments[index] = segments[index].copy(
                signature = signature,
                signatureProvider = signatureProvider,
            )
        }
        return true
    }

    fun upsertCitation(raw: CitationRecord): Boolean {
        val citation = CitationPolicy.normalize(raw) ?: return false
        val index = citations.indexOfFirst { it.sourceId == citation.sourceId }
        if (index < 0) {
            if (citations.size >= CitationPolicy.MAX_SOURCES) return false
            citations += citation
            return true
        }
        val merged = CitationPolicy.merge(citations[index], citation)
        val changed = merged != citations[index]
        citations[index] = merged
        return changed
    }
}
