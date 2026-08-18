package com.itsazni.notificationforwarder.relay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayAuthTest {

    @Test
    fun identicalSecretsMatch() {
        assertTrue(RelayAuth.secretsMatch("relay-secret-123", "relay-secret-123"))
    }

    @Test
    fun differentSecretsOfSameLengthDoNotMatch() {
        assertFalse(RelayAuth.secretsMatch("relay-secret-123", "relay-secret-456"))
    }

    @Test
    fun differentLengthSecretsDoNotMatch() {
        assertFalse(RelayAuth.secretsMatch("short", "much-longer-secret"))
    }

    @Test
    fun nullProvidedNeverMatches() {
        assertFalse(RelayAuth.secretsMatch(null, "expected-secret"))
    }

    @Test
    fun nullExpectedNeverMatches() {
        assertFalse(RelayAuth.secretsMatch("provided-secret", null))
    }

    @Test
    fun bothNullNeverMatches() {
        assertFalse(RelayAuth.secretsMatch(null, null))
    }

    @Test
    fun emptyProvidedNeverMatchesEvenAgainstEmptyExpected() {
        // Fail-closed: an unset expected secret must never be satisfiable by an empty header.
        assertFalse(RelayAuth.secretsMatch("", ""))
    }

    @Test
    fun caseSensitiveComparison() {
        assertFalse(RelayAuth.secretsMatch("Secret", "secret"))
    }

    @Test
    fun singleCharacterDifferenceIsDetected() {
        assertFalse(RelayAuth.secretsMatch("abcdefgh", "abcdefgi"))
    }
}
