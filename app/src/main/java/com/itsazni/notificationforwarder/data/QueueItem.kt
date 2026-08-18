package com.itsazni.notificationforwarder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class QueueStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED
}

@Entity(
    tableName = "notification_queue",
    indices = [Index(value = ["dedupKey"], unique = true)]
)
data class QueueItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    // Per-message sender (MessagingStyle Person.name), null when the notification had
    // no MessagingStyle to extract one from.
    val senderName: String? = null,
    val postedAt: Long,
    val notificationKey: String,
    // JSON-serialized List<ExtractedAction> (label, hasRemoteInput, remoteInputKey)
    // from the notification that produced this message. "[]" when there were none.
    val actionsJson: String = "[]",
    // Content-derived dedup key -- see DedupKey. Replaces the old UNIQUE(notificationKey)
    // constraint, which silently dropped every message after the first on a given
    // Android notification (Fas 2 checkpoint 5, task 1).
    val dedupKey: String = DedupKey.build(notificationKey, senderName ?: "", postedAt, text),
    val status: QueueStatus = QueueStatus.PENDING,
    val attemptCount: Int = 0,
    val nextRetryAt: Long = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class QueueStats(
    val pendingCount: Int,
    val sendingCount: Int,
    val sentCount: Int,
    val failedCount: Int
)
