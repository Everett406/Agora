package com.newoether.agora.viewmodel

import android.content.Context
import com.newoether.agora.R
import com.newoether.agora.api.GenerationError
import com.newoether.agora.api.LlmProvider
import com.newoether.agora.api.StreamEvent
import com.newoether.agora.model.MessageSegment
import com.newoether.agora.model.MessageStatus
import com.newoether.agora.model.RequestTokenUsageAccumulator
import com.newoether.agora.model.StreamingTextDelta
import com.newoether.agora.model.TokenUsage

/** One generation's output buffers and event transitions; execution and persistence stay outside. */
internal class GenerationOutputAccumulator(
    private val providerName: String,
) {
    val totalText = StringBuilder()
    var totalThoughts = ""
    var thinkingPlaceholder = ""
    var totalThoughtTitle: String? = null
    var totalTokenCount = 0
    var totalTokenUsage: TokenUsage? = null
    val tokenUsageAccumulator = RequestTokenUsageAccumulator()
    val thoughtTiming = GenerationThoughtTiming()
    var currentStatus = MessageStatus.SENDING
    var generationErrorMessage: String? = null
    var generationErrorCode: String? = null
    var retryText: String? = null
    val toolOverlay = GenerationToolOverlay(providerName)
    val generatedImages = mutableListOf<String>()
    var currentAnswerBuf = StringBuilder()
    var currentAnswerDeltas = mutableListOf<StreamingTextDelta>()
    var nextAnswerDeltaSequence = 0L
    var currentThoughtBuf = StringBuilder()
    var currentThoughtSignature: String? = null
    var currentThoughtSignatureProvider: String? = null

    fun flushAnswerSegment() {
        if (currentAnswerBuf.isNotEmpty()) {
            toolOverlay.append(
                MessageSegment(
                    type = "answer",
                    content = currentAnswerBuf.toString(),
                    streamingTextDeltas = currentAnswerDeltas.toList(),
                ),
            )
            currentAnswerBuf = StringBuilder()
            currentAnswerDeltas = mutableListOf()
        }
    }

    fun flushThoughtSegment() {
        thoughtTiming.finishCurrent()
        if (currentThoughtBuf.isNotEmpty()) {
            toolOverlay.append(
                MessageSegment(
                    type = "thought",
                    content = currentThoughtBuf.toString(),
                    signature = currentThoughtSignature,
                    signatureProvider = currentThoughtSignatureProvider,
                    durationMs = thoughtTiming.currentDurationMs.takeIf { it > 0L },
                ),
            )
            currentThoughtBuf = StringBuilder()
            currentThoughtSignature = null
            currentThoughtSignatureProvider = null
        }
        thoughtTiming.resetCurrentDuration()
    }

    suspend fun handleStreamEvent(
        event: StreamEvent,
        providerAnswerStart: Int,
        provider: LlmProvider,
        context: Context,
        uiUpdateGate: StreamingUiUpdateGate,
        publishStreamUpdate: suspend (Boolean) -> Unit,
        publishRetrySnapshot: () -> Unit,
    ) {
        when (event) {
            is StreamEvent.TextChunk -> if (event.text.isNotEmpty()) tokenUsageAccumulator.observeGenerationContent()
            is StreamEvent.ThoughtChunk -> if (event.thought.isNotEmpty()) tokenUsageAccumulator.observeGenerationContent()
            is StreamEvent.ToolCallUpdate -> if (event.arguments.isNotEmpty()) tokenUsageAccumulator.observeGenerationContent()
            is StreamEvent.HostedToolCallUpdate -> tokenUsageAccumulator.pauseGeneration()
            is StreamEvent.Retrying -> tokenUsageAccumulator.resetGenerationTiming()
            else -> Unit
        }
        when (event) {
            is StreamEvent.TextChunk -> {
                val answerText = if (currentStatus == MessageStatus.THINKING) event.text.trimStart() else event.text
                if (currentStatus == MessageStatus.THINKING && answerText.isBlank()) {
                    retryText = null
                    return
                }
                if (currentStatus == MessageStatus.THINKING) {
                    flushThoughtSegment()
                }
                totalText.append(answerText)
                currentAnswerBuf.append(answerText)
                val deltaCodePointCount =
                    answerText.codePointCount(0, answerText.length)
                if (deltaCodePointCount > 0) {
                    currentAnswerDeltas += StreamingTextDelta(
                        sequence = nextAnswerDeltaSequence++,
                        codePointCount = deltaCodePointCount,
                    )
                }
                if (answerText.isNotBlank()) {
                    currentStatus = MessageStatus.SENDING
                }
                retryText = null
            }
            is StreamEvent.CitationUpdate -> {
                toolOverlay.upsertCitation(
                    rebaseCitationForFinalAnswer(
                        citation = event.citation,
                        providerAnswerStart = providerAnswerStart,
                        finalAnswer = totalText.toString(),
                    ),
                )
            }
            is StreamEvent.ThoughtChunk -> {
                val updatedCompletedThought = event.thought.isEmpty() &&
                    currentStatus != MessageStatus.THINKING &&
                    (event.title != null || event.signature != null) &&
                    toolOverlay.updateLastThoughtMetadata(
                        signature = event.signature,
                        signatureProvider = provider.name.takeIf {
                            event.signature != null
                        },
                    )
                if (updatedCompletedThought) {
                    if (event.title != null) totalThoughtTitle = event.title
                    return
                }
                flushAnswerSegment()
                currentStatus = MessageStatus.THINKING
                retryText = null
                thoughtTiming.ensureStarted()
                if (totalThoughts.isEmpty()) totalThoughts = thinkingPlaceholder
                if (event.thought.isNotEmpty()) {
                    currentThoughtBuf.append(event.thought)
                    if (totalThoughts == thinkingPlaceholder) totalThoughts = event.thought
                    else totalThoughts += event.thought
                }
                if (event.title != null) totalThoughtTitle = event.title
                if (event.signature != null) {
                    currentThoughtSignature = event.signature
                    currentThoughtSignatureProvider = provider.name
                }
            }
            is StreamEvent.UsageUpdate -> {
                tokenUsageAccumulator.observeRequestSnapshot(event.usage)
                totalTokenUsage = tokenUsageAccumulator.snapshot()
                totalTokenCount = totalTokenUsage?.totalTokenCount ?: 0
                if (totalText.isEmpty() && event.thoughtsTokenCount > 0) {
                    currentStatus = MessageStatus.THINKING
                    thoughtTiming.ensureStarted()
                    if (totalThoughts.isEmpty()) totalThoughts = thinkingPlaceholder
                }
            }
            is StreamEvent.Retrying -> {
                retryText = context.getString(R.string.generation_retry_attempt, event.attempt, event.maxAttempts)
                publishRetrySnapshot()
            }
            is StreamEvent.Error -> {
                flushThoughtSegment()
                flushAnswerSegment()
                retryText = null
                currentStatus = MessageStatus.ERROR
                generationErrorMessage = localizedGenerationError(context, event.error)
                generationErrorCode = (event.error as? GenerationError.LocalModel)?.code
            }
            // A chat-only client never advertises tools, so a tool event is not something this
            // client can execute. It is dropped rather than rendered as a tool block; the
            // Provider pass is still accepted and any text it produced is kept.
            else -> Unit
        }

        val now = System.currentTimeMillis()
        val isSignificant =
            event is StreamEvent.Error || event is StreamEvent.CitationUpdate
        if (uiUpdateGate.isDue(now) || isSignificant) {
            publishStreamUpdate(isSignificant)
            uiUpdateGate.recordPublished(now)
        }
    }
}
