package com.newoether.agora.data.local

import androidx.room.Query

/**
 * Keyword conversation search inherited by the sole [ChatDao].
 *
 * The former semantic (embedding) projections are gone with the RAG subsystem, so this is now a
 * plain `LIKE` search over user/model message text plus conversation titles.
 */
interface ChatSearchDao {
    /** [query] must be pre-escaped for LIKE (see ConversationRepository.escapeLikePattern). */
    @Query("SELECT m.* FROM messages m INNER JOIN conversations c ON m.conversationId = c.id WHERE c.taskId IS NULL AND (m.text LIKE '%' || :query || '%' ESCAPE '\\' OR c.title LIKE '%' || :query || '%' ESCAPE '\\') AND m.participant IN ('USER', 'MODEL') AND m.text != '' AND substr(m.id, 1, 5) != 'tool_' AND substr(m.id, 1, 7) != 'result_' ORDER BY m.timestamp DESC, m.id DESC LIMIT :limit")
    suspend fun searchMessages(query: String, limit: Int = 10): List<MessageEntity>

    @Query("SELECT m.* FROM messages m INNER JOIN conversations c ON m.conversationId = c.id WHERE c.taskId IS NULL AND m.toolCallJson LIKE '%\"type\":\"citation\"%' AND m.participant = 'MODEL' AND substr(m.id, 1, 5) != 'tool_' AND substr(m.id, 1, 7) != 'result_' AND m.id > :afterId ORDER BY m.id ASC LIMIT :pageSize")
    suspend fun getMessagesWithCitationSegmentsPage(
        afterId: String,
        pageSize: Int,
    ): List<MessageEntity>
}
