package com.itsazni.notificationforwarder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ReplyReceiptStatus { SENT_ATTEMPTED, CONFIRMED, FAILED }

/**
 * Durable evidence of a reply-relay attempt (Fas 2 checkpoint 8, task 3). A row is inserted with
 * status SENT_ATTEMPTED *before* the PendingIntent.send() call, so a process death mid-send
 * still leaves a record that an attempt was made -- it just never reaches CONFIRMED. Only the
 * uuid/notificationKey/timestamps are stored; the reply text itself is never persisted here.
 */
@Entity(tableName = "reply_receipts")
data class ReplyReceipt(
    @PrimaryKey val id: String,
    val notificationKey: String,
    val status: ReplyReceiptStatus,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Pure state-machine logic for ReplyReceipt.status transitions. Only SENT_ATTEMPTED may move to
 * CONFIRMED or FAILED -- both are terminal. Framework-free (no Room/Android imports) so it's
 * plain-JVM-testable independent of the entity/DAO.
 */
object ReplyReceiptTransitions {
    fun confirm(current: ReplyReceiptStatus): ReplyReceiptStatus {
        require(current == ReplyReceiptStatus.SENT_ATTEMPTED) {
            "cannot confirm from $current"
        }
        return ReplyReceiptStatus.CONFIRMED
    }

    fun fail(current: ReplyReceiptStatus): ReplyReceiptStatus {
        require(current == ReplyReceiptStatus.SENT_ATTEMPTED) {
            "cannot fail from $current"
        }
        return ReplyReceiptStatus.FAILED
    }
}
