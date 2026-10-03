package fr.president.engine.simulation

import fr.president.engine.time.WorldTime

/** File d'actions planifiées dans le temps du monde. */
class Scheduler(private val state: SchedulerState) {

    fun schedule(action: ScheduledAction) {
        state.actions.add(action)
    }

    /** Retire et renvoie les actions dues, dans l'ordre chronologique. */
    fun takeDue(now: WorldTime): List<ScheduledAction> {
        val due = state.actions.filter { it.at <= now }.sortedBy { it.at }
        if (due.isNotEmpty()) state.actions.removeAll(due.toSet())
        return due
    }

    fun pending(): List<ScheduledAction> = state.actions.sortedBy { it.at }
}
