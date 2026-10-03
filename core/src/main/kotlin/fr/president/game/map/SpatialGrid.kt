package fr.president.game.map

import com.badlogic.gdx.math.Rectangle

/**
 * Index spatial par grille uniforme : retrouve rapidement les éléments proches d'un point
 * ou visibles dans une zone, sans parcourir toute la carte.
 */
class SpatialGrid<T>(private val cellSize: Float) {
    private val cells = HashMap<Long, MutableList<T>>()

    fun insert(item: T, bounds: Rectangle) {
        forCells(bounds) { key -> cells.getOrPut(key) { mutableListOf() }.add(item) }
    }

    fun query(area: Rectangle, out: MutableSet<T> = LinkedHashSet()): Set<T> {
        forCells(area) { key -> cells[key]?.let { out.addAll(it) } }
        return out
    }

    fun at(x: Float, y: Float): List<T> = cells[key(cell(x), cell(y))].orEmpty()

    private inline fun forCells(r: Rectangle, action: (Long) -> Unit) {
        for (cx in cell(r.x)..cell(r.x + r.width)) for (cy in cell(r.y)..cell(r.y + r.height)) action(key(cx, cy))
    }

    private fun cell(v: Float) = Math.floorDiv(v.toInt(), cellSize.toInt().coerceAtLeast(1))
    private fun key(cx: Int, cy: Int) = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffffL)
}
