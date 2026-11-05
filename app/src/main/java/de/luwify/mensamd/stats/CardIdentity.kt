package de.luwify.mensamd.stats

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Recognizing "Meine Karte" without storing its UID. The NFC layer turns the UID into a [fingerprint] right away (the
 * raw UID never leaves the NFC callback); only a salted hash of the fingerprint ([hash]) is stored. The UID of the
 * Studentenwerk's cards is fixed (7 bytes, not random; checked on a real card 2026-11-05), so the hash is stable.
 */
object CardIdentity {
    private const val DOMAIN = "mensamd-card-v1:"

    /** SHA-256 over a domain prefix and the UID, hex. Kept in memory only. */
    fun fingerprint(uid: ByteArray): String = sha256(DOMAIN.toByteArray() + uid).toHex()

    fun newSalt(random: SecureRandom = SecureRandom()): String = ByteArray(16).also(random::nextBytes).toHex()

    /**
     * What is stored: PBKDF2-HMAC-SHA256 of the fingerprint with the salt, hex. Deliberately slow: a 7-byte NXP UID has
     * only about 2^48 values, so a fast hash next to its salt (both are in the backup) could be brute-forced back to the
     * UID; [ITERATIONS] rounds make that impractical. About 0.1 s per scan on a phone.
     */
    fun hash(salt: String, fingerprint: String): String {
        val spec = PBEKeySpec(fingerprint.toCharArray(), salt.hexToBytes(), ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded.toHex()
    }

    const val ITERATIONS = 120_000

    fun matches(card: RegisteredCard, fingerprint: String): Boolean = hash(card.salt, fingerprint) == card.hash

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
