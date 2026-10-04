package fr.president.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable

/**
 * Habillage de l'interface construit par programme (aucun fichier de skin externe).
 * Les polices sont générées à la densité de l'écran pour rester nettes sur mobile.
 */
class UiSkin(private val scale: Float) : Disposable {
    val skin = Skin()
    val white: TextureRegion
    private val texture: Texture

    init {
        val pixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888).apply { setColor(Color.WHITE); fill() }
        texture = Texture(pixmap)
        pixmap.dispose()
        white = TextureRegion(texture)
        skin.add("white", texture)
        createFonts()
        createStyles()
    }

    fun font(name: String): BitmapFont = skin.getFont(name)
    fun fill(color: Color): Drawable = (TextureRegionDrawable(white).tint(color))

    private fun createFonts() {
        val regular = FreeTypeFontGenerator(Gdx.files.internal(REGULAR))
        val bold = FreeTypeFontGenerator(Gdx.files.internal(BOLD))
        FONT_SIZES.forEach { (name, size) -> skin.add(name, generate(regular, size)) }
        BOLD_SIZES.forEach { (name, size) -> skin.add(name, generate(bold, size)) }
        regular.dispose()
        bold.dispose()
    }

    private fun generate(gen: FreeTypeFontGenerator, size: Int): BitmapFont {
        val params = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            this.size = (size * scale).toInt().coerceAtLeast(MIN_PIXEL_SIZE)
            characters = FreeTypeFontGenerator.DEFAULT_CHARS + EXTRA_CHARS
            minFilter = Texture.TextureFilter.Linear
            magFilter = Texture.TextureFilter.Linear
        }
        return gen.generateFont(params).apply {
            data.setScale(1f / scale)
            setUseIntegerPositions(false)
        }
    }

    private fun createStyles() {
        LABELS.forEach { (style, font, color) -> skin.add(style, Label.LabelStyle(font(font), color)) }
        skin.add("default", button(Theme.button, Theme.buttonOver, Theme.buttonDown, null))
        skin.add("accent", button(Theme.accentDark, Theme.accent, Theme.accent, null))
        skin.add("toggle", button(Theme.button, Theme.buttonOver, Theme.buttonDown, Theme.accentDark))
        skin.add("flat", button(Color.CLEAR, Theme.buttonOver, Theme.buttonDown, Theme.accentDark))
        skin.add("default", ScrollPane.ScrollPaneStyle().apply {
            vScrollKnob = fill(Theme.panelBorder).also { it.minWidth = SCROLL_KNOB }
        })
        skin.add("default", TextField.TextFieldStyle().apply {
            font = font("body")
            fontColor = Theme.text
            background = fill(Theme.panelAlt).also { it.leftWidth = FIELD_PADDING; it.rightWidth = FIELD_PADDING }
            cursor = fill(Theme.accent).also { it.minWidth = 2f }
            selection = fill(Theme.accentDark)
        })
    }

    /** Style de bouton plein d'une couleur vive (créé à la demande, puis réutilisé). */
    fun colorButtonStyle(color: Color): TextButton.TextButtonStyle {
        val name = "color-" + color.toString()
        if (skin.has(name, TextButton.TextButtonStyle::class.java)) return skin.get(name, TextButton.TextButtonStyle::class.java)
        val style = button(color.cpy().mul(DARKEN, DARKEN, DARKEN, 1f), color, color.cpy().lerp(Color.WHITE, LIGHTEN), color).apply {
            font = font("bodyBold")
            fontColor = Color.WHITE
        }
        skin.add(name, style)
        return style
    }

    private fun button(up: Color, over: Color, down: Color, checked: Color?) = TextButton.TextButtonStyle().apply {
        this.up = fill(up).padded()
        this.over = fill(over).padded()
        this.down = fill(down).padded()
        checked?.let { this.checked = fill(it).padded() }
        font = font("body")
        fontColor = Theme.text
        disabledFontColor = Theme.textMuted
    }

    private fun Drawable.padded(): Drawable = apply {
        leftWidth = BUTTON_PAD_X; rightWidth = BUTTON_PAD_X; topHeight = BUTTON_PAD_Y; bottomHeight = BUTTON_PAD_Y
    }

    override fun dispose() {
        skin.dispose()
    }

    private companion object {
        const val DARKEN = 0.78f
        const val LIGHTEN = 0.25f
        const val REGULAR = "fonts/DejaVuSans.ttf"
        const val BOLD = "fonts/DejaVuSans-Bold.ttf"
        const val EXTRA_CHARS = "àâäæçéèêëîïôöœùûüÿÀÂÄÆÇÉÈÊËÎÏÔÖŒÙÛÜŸ€–—’‘“”«»…•→←↑↓★·°²✕−▲▼▶●◆♥⚔⚖⚙✉⚡☀✚⚑⌂☎✈⚓☢⚒⚕♻☰✔✖↻⚠☮◀‖⊘⌘▣◎☼⚇⚐⚲✂✎✪✹✿❄"
        const val MIN_PIXEL_SIZE = 8
        const val BUTTON_PAD_X = 10f
        const val BUTTON_PAD_Y = 6f
        const val SCROLL_KNOB = 4f
        const val FIELD_PADDING = 8f
        val FONT_SIZES = listOf("small" to 12, "body" to 14, "large" to 17)
        val BOLD_SIZES = listOf("bodyBold" to 14, "valueBold" to 17, "title" to 20, "headline" to 26)
        val LABELS = listOf(
            Triple("default", "body", Theme.text),
            Triple("muted", "small", Theme.textMuted),
            Triple("small", "small", Theme.text),
            Triple("bold", "bodyBold", Theme.text),
            Triple("large", "large", Theme.text),
            Triple("value", "valueBold", Theme.text),
            Triple("title", "title", Theme.text),
            Triple("headline", "headline", Theme.text),
        )
    }
}
