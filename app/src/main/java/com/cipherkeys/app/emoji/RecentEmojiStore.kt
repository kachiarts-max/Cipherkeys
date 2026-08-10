package com.cipherkeys.app.emoji

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.emojiDataStore by preferencesDataStore(name = "cipherkeys_emoji")

/**
 * Tracks the user's most-recently-used emoji, most-recent-first, capped at [MAX_RECENTS].
 * Stored as a single comma-joined preference (emoji themselves never contain commas, so
 * this is safe) - kept as its own small DataStore rather than folded into
 * [com.cipherkeys.app.data.SettingsRepository] since it's a different kind of state
 * (usage history, not a user preference).
 */
class RecentEmojiStore(private val context: Context) {

    private object Keys {
        val RECENTS = stringPreferencesKey("recent_emoji")
    }

    val recentsFlow: Flow<List<String>> = context.emojiDataStore.data.map { prefs ->
        prefs[Keys.RECENTS]?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    suspend fun addRecent(emoji: String) {
        context.emojiDataStore.edit { prefs ->
            val current = prefs[Keys.RECENTS]?.split(",")?.filter { it.isNotEmpty() }?.toMutableList()
                ?: mutableListOf()
            current.remove(emoji)
            current.add(0, emoji)
            prefs[Keys.RECENTS] = current.take(MAX_RECENTS).joinToString(",")
        }
    }

    companion object {
        private const val MAX_RECENTS = 30
    }
}
