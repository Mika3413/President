package fr.president.game.ui.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Disposable
import fr.president.engine.politics.Character
import fr.president.engine.util.GameRandom

/**
 * Portraits procéduraux : chaque personnage fictif a un visage stable, dérivé de sa graine
 * (teint, coiffure, couleur de cheveux, lunettes, tenue). Aucune image de personne réelle.
 */
class Portraits : Disposable {
    private val cache = HashMap<String, Texture>()

    fun image(c: Character, size: Float = DEFAULT_SIZE): Image =
        Image(TextureRegion(cache.getOrPut(key(c)) { draw(c) })).apply { setSize(size, size) }

    /** Le visage change avec les choix du joueur : la clé du cache en tient compte. */
    private fun key(c: Character) = "${c.id}|${c.appearance}|${c.female}|${c.birthYear}"

    private fun draw(c: Character): Texture {
        val r = GameRandom(c.portraitSeed)
        val a = c.appearance
        val p = Pixmap(SIZE, SIZE, Pixmap.Format.RGBA8888)
        p.setColor(BACKGROUNDS[a?.background?.mod(BACKGROUNDS.size) ?: r.nextInt(BACKGROUNDS.size)]); p.fill()
        val skin = SKINS[a?.skin?.mod(SKINS.size) ?: r.nextInt(SKINS.size)]
        val hair = if (a != null) (if (a.grey) GREY else HAIR[a.hair.mod(HAIR.size)])
            else HAIR[r.nextInt(HAIR.size)].let { if (c.birthYear < GREY_BEFORE && r.chance(GREY_CHANCE)) GREY else it }
        val suit = SUITS[a?.suit?.mod(SUITS.size) ?: r.nextInt(SUITS.size)]
        val cx = SIZE / 2
        // Épaules et tenue.
        p.setColor(suit); p.fillCircle(cx, SIZE + 18, 34)
        p.setColor(Color.WHITE); p.fillTriangle(cx - 6, SIZE - 22, cx + 6, SIZE - 22, cx, SIZE - 10)
        if (!c.female) { p.setColor(TIES[a?.accent?.mod(TIES.size) ?: r.nextInt(TIES.size)]); p.fillTriangle(cx - 2, SIZE - 20, cx + 2, SIZE - 20, cx, SIZE - 8) }
        // Cou et visage.
        p.setColor(skin); p.fillRectangle(cx - 6, 36, 12, 12)
        val faceW = 14 + r.nextInt(3)
        p.fillCircle(cx, 27, faceW)
        // Cheveux.
        p.setColor(hair)
        val bald = if (a != null) a.hairStyle == STYLE_BALD else !c.female && r.chance(BALD_CHANCE)
        if (!bald) {
            p.fillRectangle(cx - faceW, 12, faceW * 2, 7)
            p.fillCircle(cx, 16, faceW - 2)
            val long = if (a != null) a.hairStyle == STYLE_LONG else c.female && r.chance(LONG_HAIR_CHANCE)
            if (long) { p.fillRectangle(cx - faceW - 2, 18, 5, 26); p.fillRectangle(cx + faceW - 3, 18, 5, 26) }
            if (a?.hairStyle == STYLE_BUN) p.fillCircle(cx, 7, 6)
            p.setColor(skin); p.fillCircle(cx, 30, faceW - 3)
        }
        // Yeux, sourcils, bouche.
        p.setColor(EYES); p.fillCircle(cx - 5, 27, 1); p.fillCircle(cx + 5, 27, 1)
        p.setColor(hair); p.drawLine(cx - 7, 23, cx - 3, 23); p.drawLine(cx + 3, 23, cx + 7, 23)
        p.setColor(MOUTH); p.drawLine(cx - 4, 35, cx + 4, 35)
        if (a?.glasses ?: r.chance(GLASSES_CHANCE)) { p.setColor(GLASSES); p.drawCircle(cx - 5, 27, 3); p.drawCircle(cx + 5, 27, 3); p.drawLine(cx - 2, 27, cx + 2, 27) }
        val texture = Texture(p)
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        p.dispose()
        return texture
    }

    override fun dispose() {
        cache.values.forEach { it.dispose() }
        cache.clear()
    }

    companion object {
        const val SIZE = 64
        const val STYLE_LONG = 1
        const val STYLE_BALD = 2
        const val STYLE_BUN = 3
        const val DEFAULT_SIZE = 44f
        /** Nombre de choix proposés pour chaque élément du visage. */
        val CHOICES get() = mapOf("skin" to SKINS.size, "hair" to HAIR.size, "suit" to SUITS.size, "accent" to TIES.size, "background" to BACKGROUNDS.size, "hairStyle" to 4)
        const val GREY_BEFORE = 1966
        const val GREY_CHANCE = 0.6
        const val BALD_CHANCE = 0.2
        const val LONG_HAIR_CHANCE = 0.6
        const val GLASSES_CHANCE = 0.3
        val BACKGROUNDS = listOf("2f4255", "3a4a5c", "2b3b4b", "41505f").map { Color.valueOf(it) }
        val SKINS = listOf("f1c6a5", "e0ac85", "c68c5f", "a8693f", "7d4a2a", "f5d3b8").map { Color.valueOf(it) }
        val HAIR = listOf("2b1d14", "4a3121", "6b4a2f", "a0773f", "1a1a1a", "8c5a3c").map { Color.valueOf(it) }
        val GREY: Color = Color.valueOf("b9b9b9")
        val SUITS = listOf("1f2a44", "2d2d2d", "3b4a5a", "4a3b3b", "263238").map { Color.valueOf(it) }
        val TIES = listOf("8e2b2b", "2b4a8e", "3b6b3b", "6b5b2b").map { Color.valueOf(it) }
        val EYES: Color = Color.valueOf("222222")
        val MOUTH: Color = Color.valueOf("8a4b4b")
        val GLASSES: Color = Color.valueOf("333333")
    }
}
