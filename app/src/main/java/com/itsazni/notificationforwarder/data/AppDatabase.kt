package com.itsazni.notificationforwarder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// v1 -> v2 (Fas 2 checkpoint 5, task 1): dedup moved from UNIQUE(notificationKey) to a
// content-derived dedupKey (QueueItem/DedupKey), and senderName/actionsJson columns
// were added. Destructive migration instead of a hand-written Migration object:
// notification_queue only holds transient forwarding state -- SENT rows are just
// local history for the in-app Queue tab, nothing server-side depends on them
// surviving an app update, and any PENDING/SENDING rows lost on this one-time reset
// get re-created the next time the source app reposts the notification. This repo has
// no JVM DB/migration test harness, so an unverifiable ALTER TABLE migration (this
// build has no local Android/JDK toolchain to compile-check it against) is a bigger
// real risk than a documented, bounded data reset on a single-device personal queue.
@Database(entities = [QueueItem::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notif_forwarder.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
