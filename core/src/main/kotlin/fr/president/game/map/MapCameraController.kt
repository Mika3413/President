package fr.president.game.map

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3

/**
 * Déplacement fluide, zoom au pincement ou à la molette (centré sur le curseur),
 * et toucher simple pour sélectionner.
 */
class MapCameraController(
    private val camera: OrthographicCamera,
    private val onTap: (screenX: Float, screenY: Float) -> Unit,
) : GestureDetector.GestureAdapter() {
    private var initialZoom = camera.zoom
    private val tmp = Vector3()

    val gestureDetector = GestureDetector(this)
    val scrollProcessor = object : InputAdapter() {
        override fun scrolled(amountX: Float, amountY: Float): Boolean {
            zoomAt(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), if (amountY > 0) WHEEL_FACTOR else 1f / WHEEL_FACTOR)
            return true
        }
    }

    override fun touchDown(x: Float, y: Float, pointer: Int, button: Int): Boolean {
        initialZoom = camera.zoom
        return false
    }

    override fun tap(x: Float, y: Float, count: Int, button: Int): Boolean {
        if (count >= 2) zoomAt(x, y, 1f / DOUBLE_TAP_FACTOR) else onTap(x, y)
        return true
    }

    override fun pan(x: Float, y: Float, deltaX: Float, deltaY: Float): Boolean {
        camera.translate(-deltaX * camera.zoom, deltaY * camera.zoom)
        clamp()
        return true
    }

    override fun zoom(initialDistance: Float, distance: Float): Boolean {
        camera.zoom = (initialZoom * initialDistance / distance).coerceIn(MIN_ZOOM, MAX_ZOOM)
        camera.update()
        return true
    }

    override fun pinch(initialPointer1: Vector2, initialPointer2: Vector2, pointer1: Vector2, pointer2: Vector2): Boolean = false

    fun zoomAt(screenX: Float, screenY: Float, factor: Float) {
        tmp.set(screenX, screenY, 0f)
        camera.unproject(tmp)
        val beforeX = tmp.x
        val beforeY = tmp.y
        camera.zoom = (camera.zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        camera.update()
        tmp.set(screenX, screenY, 0f)
        camera.unproject(tmp)
        camera.translate(beforeX - tmp.x, beforeY - tmp.y)
        clamp()
    }

    /** Centre la vue sur un point monde avec une largeur visible donnée (unités monde). */
    fun focus(x: Float, y: Float, visibleWidth: Float) {
        camera.position.set(x, y, 0f)
        camera.zoom = (visibleWidth / camera.viewportWidth).coerceIn(MIN_ZOOM, MAX_ZOOM)
        clamp()
    }

    private fun clamp() {
        camera.position.x = camera.position.x.coerceIn(MIN_X, MAX_X)
        camera.position.y = camera.position.y.coerceIn(MIN_Y, MAX_Y)
        camera.update()
    }

    companion object {
        const val MIN_ZOOM = 0.03f
        const val MAX_ZOOM = 30f
        private const val WHEEL_FACTOR = 1.15f
        private const val DOUBLE_TAP_FACTOR = 2f
        private val MIN_X = GeoProjection.x(-180.0)
        private val MAX_X = GeoProjection.x(180.0)
        private val MIN_Y = GeoProjection.y(-60.0)
        private val MAX_Y = GeoProjection.y(80.0)
    }
}
