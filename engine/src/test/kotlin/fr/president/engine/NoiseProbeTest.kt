package fr.president.engine

import kotlin.test.Test

class NoiseProbeTest {
    @Test
    fun probe() {
        if (System.getProperty("audit") != "noise") return
        val clock = TestData.FakeClock()
        val s = TestData.newSession(seed = 3L, clock = clock)
        val seen = HashSet<Long>()
        val rows = ArrayList<String>()
        repeat(365) {
            clock.advanceWorldDays(1.0); s.advanceToNow()
            s.state.notifications.feed.forEach { n -> if (seen.add(n.id)) rows += "${n.urgency}|${n.category}|${n.title.take(40).replace(Regex("[0-9]"), "#")}" }
            s.state.inbox.messages.filter { it.awaitingAnswer }.forEach { m -> m.defaultOptionId?.let { s.answer(m.id, it) } }
        }
        println("NOISE total=${rows.size} " + rows.groupingBy { it.substringBefore('|') }.eachCount())
        rows.filter { !it.startsWith("INFO") }.groupingBy { it.substringAfter('|') }.eachCount().entries.sortedByDescending { it.value }.take(45).forEach { println("NOISE ${it.value} ${it.key}") }
        println("NOISE inbox=${s.state.inbox.messages.size}")
        rows.filter { !it.startsWith("INFO") }.groupingBy { r -> val t = r.substringAfter('|'); t.substringBefore('|') + " " + t.substringAfter('|').split(' ').take(3).joinToString(" ") }.eachCount()
            .entries.sortedByDescending { it.value }.take(25).forEach { println("NOISEK ${it.value} ${it.key}") }
        rows.filter { !it.startsWith("INFO") }.groupingBy { it.substringAfter('|').substringBefore('|') }.eachCount().forEach { println("NOISEC $it") }
    }
}
