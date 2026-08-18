package com.itsazni.notificationforwarder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaseRecoveryTest {

    private val now = 1_000_000L

    @Test
    fun sendingRowOlderThanLeaseTimeoutIsStale() {
        val updatedAt = now - LeaseRecovery.LEASE_TIMEOUT_MS - 1
        assertTrue(LeaseRecovery.isStale(QueueStatus.SENDING, updatedAt, now))
    }

    @Test
    fun sendingRowExactlyAtLeaseTimeoutIsStale() {
        val updatedAt = now - LeaseRecovery.LEASE_TIMEOUT_MS
        assertTrue(LeaseRecovery.isStale(QueueStatus.SENDING, updatedAt, now))
    }

    @Test
    fun sendingRowWithinLeaseWindowIsNotStale() {
        val updatedAt = now - LeaseRecovery.LEASE_TIMEOUT_MS + 1
        assertFalse(LeaseRecovery.isStale(QueueStatus.SENDING, updatedAt, now))
    }

    @Test
    fun pendingRowIsNeverStaleRegardlessOfAge() {
        val updatedAt = now - LeaseRecovery.LEASE_TIMEOUT_MS - 1
        assertFalse(LeaseRecovery.isStale(QueueStatus.PENDING, updatedAt, now))
    }

    @Test
    fun sentRowIsNeverStale() {
        val updatedAt = now - LeaseRecovery.LEASE_TIMEOUT_MS - 1
        assertFalse(LeaseRecovery.isStale(QueueStatus.SENT, updatedAt, now))
    }

    @Test
    fun failedRowIsNeverStale() {
        val updatedAt = now - LeaseRecovery.LEASE_TIMEOUT_MS - 1
        assertFalse(LeaseRecovery.isStale(QueueStatus.FAILED, updatedAt, now))
    }

    @Test
    fun cutoffIsNowMinusTimeout() {
        assertEquals(now - LeaseRecovery.LEASE_TIMEOUT_MS, LeaseRecovery.cutoff(now))
    }

    @Test
    fun cutoffRespectsCustomTimeout() {
        val customTimeout = 60_000L
        assertEquals(now - customTimeout, LeaseRecovery.cutoff(now, customTimeout))
    }
}
