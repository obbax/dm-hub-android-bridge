package com.itsazni.notificationforwarder.relay

/**
 * Constant-time shared-secret comparison for the reply-relay's `X-DM-Hub-Secret` header,
 * mirroring dm-hub's own constantTimeEqual (src/ingest/signature.js) so a timing side-channel
 * can't leak the secret from either side of the bridge. Fail-closed: a missing/blank provided
 * or expected value never matches.
 */
object RelayAuth {
    fun secretsMatch(provided: String?, expected: String?): Boolean {
        if (provided.isNullOrEmpty() || expected.isNullOrEmpty()) return false
        if (provided.length != expected.length) return false
        var diff = 0
        for (i in provided.indices) {
            diff = diff or (provided[i].code xor expected[i].code)
        }
        return diff == 0
    }
}
