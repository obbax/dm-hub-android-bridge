package com.itsazni.notificationforwarder.data

/**
 * Pure staleness logic for the SENDING lease. QueueDao.getPending only ever reads
 * PENDING rows, so if the process dies between markSending() and markSent()/
 * markFailure(), that row is stuck in SENDING forever unless something recovers it.
 *
 * Pure Kotlin, no Android imports -- see LeaseRecoveryTest for JVM unit tests.
 */
object LeaseRecovery {
    const val LEASE_TIMEOUT_MS: Long = 5 * 60 * 1000L

    /** Any SENDING row last touched at or before this timestamp is stale. */
    fun cutoff(now: Long, timeoutMs: Long = LEASE_TIMEOUT_MS): Long = now - timeoutMs

    fun isStale(status: QueueStatus, updatedAt: Long, now: Long, timeoutMs: Long = LEASE_TIMEOUT_MS): Boolean {
        return status == QueueStatus.SENDING && updatedAt <= cutoff(now, timeoutMs)
    }
}
