package com.itsazni.notificationforwarder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ReplyReceiptDao {
    @Insert
    suspend fun insert(receipt: ReplyReceipt)

    @Query("UPDATE reply_receipts SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: ReplyReceiptStatus, updatedAt: Long)

    @Query("SELECT * FROM reply_receipts WHERE id = :id")
    suspend fun findById(id: String): ReplyReceipt?
}
