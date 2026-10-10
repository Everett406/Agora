package com.newoether.agora.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Erases everything the deleted subsystems owned.
 *
 * The app is now a local-model chat client: memory, RAG, MCP, automation and remote sessions are
 * gone, so the semantic-search index is dead weight and the stored memory payloads are data the
 * user asked to be removed.
 *
 * The two semantic ledgers leave the schema entirely (their entities and DAO are gone), so they
 * are dropped. The remaining tables stay declared so an archive written by an older build still
 * validates and imports; only their contents are cleared.
 *
 * Conversations, messages, attachments, runs, settings and API keys are never touched.
 */
val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `semantic_index_ledger`")
        db.execSQL("DROP TABLE IF EXISTS `semantic_index_work`")
        db.execSQL("DELETE FROM `embeddings`")
        db.execSQL("DELETE FROM `tasks`")
        db.execSQL("DELETE FROM `loops`")
    }
}
