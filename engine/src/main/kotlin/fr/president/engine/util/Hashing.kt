package fr.president.engine.util

/** Hachage FNV-1a 64 bits : stable entre plateformes et versions, utilisé pour les signatures de messages. */
object Hashing {
    private const val OFFSET_BASIS = -0x340d631b7bdddcdbL
    private const val PRIME = 0x100000001b3L

    fun fnv1a64(text: String): Long {
        var hash = OFFSET_BASIS
        for (ch in text) {
            hash = hash xor ch.code.toLong()
            hash *= PRIME
        }
        return hash
    }

    /** Signature normalisée : insensible à la casse, aux espaces et à la ponctuation. */
    fun messageSignature(text: String): Long =
        fnv1a64(text.lowercase().filter { it.isLetterOrDigit() })
}
