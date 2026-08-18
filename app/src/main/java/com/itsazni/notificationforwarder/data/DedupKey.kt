package com.itsazni.notificationforwarder.data

/**
 * Stable per-message dedup key. Android reuses `sbn.key` (== notificationKey) across
 * every update of a conversation notification, so keying only on notificationKey
 * (the old UNIQUE constraint) silently dropped every message after the first in a
 * burst. Keying on notificationKey + sender + this message's own timestamp + a text
 * hash means: identical reposts of a message dedup to the same row (IGNORE on
 * conflict), but a genuinely new message on the same notification gets its own row.
 *
 * Pure Kotlin, no Android imports -- see DedupKeyTest for JVM unit tests.
 */
object DedupKey {
    fun build(notificationKey: String, senderName: String, messageTimestamp: Long, text: String): String {
        val textHash = text.hashCode()
        return "$notificationKey|$senderName|$messageTimestamp|$textHash"
    }
}
