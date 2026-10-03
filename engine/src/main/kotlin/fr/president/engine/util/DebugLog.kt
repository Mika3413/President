package fr.president.engine.util

/**
 * Journal de debug interne : explique les décisions de l'IA et des systèmes.
 * Non sauvegardé (tampon circulaire), consultable depuis l'écran de debug.
 */
class DebugLog(private val capacity: Int = DEFAULT_CAPACITY) {
    data class Entry(val worldSeconds: Long, val category: String, val message: String)

    private val entries = ArrayDeque<Entry>()
    var echoToStdout: Boolean = false

    fun log(worldSeconds: Long, category: String, message: String) {
        if (entries.size >= capacity) entries.removeFirst()
        val entry = Entry(worldSeconds, category, message)
        entries.addLast(entry)
        if (echoToStdout) println("[$category] $message")
    }

    fun recent(category: String? = null, limit: Int = capacity): List<Entry> =
        entries.filter { category == null || it.category == category }.takeLast(limit)

    companion object {
        const val DEFAULT_CAPACITY = 1000
    }
}
