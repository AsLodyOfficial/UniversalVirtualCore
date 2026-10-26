package com.universal.virtual.integrity.hashing

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class IntegrityHashStore {
    private val trustedHashes = ConcurrentHashMap<String, String>()
    private val observedHashes = ConcurrentHashMap<String, String>()

    fun calculateSha256(file: File): String {
        if (!file.exists() || !file.isFile) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun recordTrustedHash(key: String, hash: String) {
        trustedHashes[key] = hash
    }

    fun recordObservedHash(key: String, hash: String) {
        observedHashes[key] = hash
    }

    fun getTrustedHash(key: String): String? = trustedHashes[key]
    fun getObservedHash(key: String): String? = observedHashes[key]

    fun verifyHash(key: String, file: File): Boolean {
        val trusted = trustedHashes[key] ?: return true
        val current = calculateSha256(file)
        observedHashes[key] = current
        return trusted.equals(current, ignoreCase = true)
    }

    fun invalidateHash(key: String) {
        trustedHashes.remove(key)
        observedHashes.remove(key)
    }

    fun clear() {
        trustedHashes.clear()
        observedHashes.clear()
    }
}
