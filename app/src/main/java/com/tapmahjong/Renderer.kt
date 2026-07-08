package com.tapmahjong

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.tapmahjong.engine.GameState
import com.tapmahjong.engine.GameEngine
import com.tapmahjong.engine.Layout
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** All drawing in 640x480 logical space on pure black (waveguide = black transparent). */
class Renderer(private val engine: GameEngine, private val store: SettingsStore) {

    private val W = 640f
    private val H = 480f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    private val rf = RectF()

    private val ivory = 0xFFEDE7D2.toInt()
    private val ivoryDim = 0xFFB7B29C.toInt()
    private val tileRim = 0xFF9A927A.toInt()
    private val tileEdge = 0xFF6E6752.toInt()

    private val suitColors = intArrayOf(0xFF3F86FF.toInt(), 0xFF37B85E.toInt(), 0xFFF0574F.toInt())
    private val slate = 0xFF5A6A86.toInt()
    private val magenta = 0xFFD44CB0.toInt()
    private val teal = 0xFF1FA894.toInt()

    fun draw(c: Canvas, w: Int, h: Int) {
        c.drawColor(Color.BLACK)
        if (w <= 0 || h <= 0) return
        val s = min(w / W, h / H)
        c.save()
        c.translate((w - W * s) / 2f, (h - H * s) / 2f)
        c.scale(s, s)

        if (engine.state == GameState.MENU) drawMenu(c)
        else {
            drawTiles(c)
            drawHud(c)
            drawParticles(c)
            when (engine.state) {
                GameState.WON -> drawWon(c)
                GameState.STUCK -> drawStuck(c)
                else -> {}
            }
            drawInvalid(c)
        }
        if (engine.settingsOpen) drawSettings(c)
        c.restore()
    }

    // ------------------------------------------------------------- tiles

    private fun drawTiles(c: Canvas) {
        val tw = engine.tileW(); val th = engine.tileH()
        val hint = engine.hintPair
        for (i in engine.drawOrder) {
            if (!engine.board.present[i]) continue
            val cx = engine.tileCX(i); val cy = engine.tileCY(i)
            val free = engine.board.isFree(i)
            drawTile(c, cx, cy, tw, th, engine.board.face[i], free)
            if (i == engine.selected) outline(c, cx, cy, tw, th, Color.argb(255, 90, 230, 120), 3.5f)
            if (hint != null && (i == hint.first || i == hint.second)) {
                val a = (140 + 100 * sin(engine.time * 6f)).toInt().coerceIn(60, 255)
                outline(c, cx, cy, tw, th, Color.argb(a, 255, 210, 90), 3f)
            }
        }
        if (engine.board.present[engine.cursor]) {
            val cx = engine.tileCX(engine.cursor); val cy = engine.tileCY(engine.cursor)
            val pz = (200 + 55 * sin(engine.time * 5f)).toInt().coerceIn(90, 255)
            outline(c, cx, cy, tw, th, Color.argb(pz, 255, 255, 255), 2.5f)
            if (engine.selected >= 0 && engine.board.canMatch(engine.selected, engine.cursor)) {
                rf.set(cx - tw * 0.5f, cy - th * 0.5f, cx + tw * 0.5f, cy + th * 0.5f)
                stroke.strokeWidth = 4f
                if (engine.moveArmed) {
                    stroke.color = Color.argb((200 + 55 * sin(engine.time * 6f)).toInt().coerceIn(120, 255), 120, 240, 150)
                    c.drawArc(rf, -90f, 360f, false, stroke)
                } else {
                    stroke.color = Color.argb(230, 255, 210, 120)
                    c.drawArc(rf, -90f, 360f * engine.dwellProgress, false, stroke)
                }
            }
        }
    }

    private fun outline(c: Canvas, cx: Float, cy: Float, tw: Float, th: Float, color: Int, width: Float) {
        rf.set(cx - tw / 2, cy - th / 2, cx + tw / 2, cy + th / 2)
        stroke.strokeWidth = width; stroke.color = color
        c.drawRoundRect(rf, 5f, 5f, stroke)
    }

    private fun drawTile(c: Canvas, cx: Float, cy: Float, tw: Float, th: Float, face: Int, free: Boolean) {
        fill.shader = null
        fill.color = tileEdge
        rf.set(cx - tw / 2 + 2.5f, cy - th / 2 + 3f, cx + tw / 2 + 2.5f, cy + th / 2 + 3f)
        c.drawRoundRect(rf, 5f, 5f, fill)
        fill.color = if (free) ivory else ivoryDim
        rf.set(cx - tw / 2, cy - th / 2, cx + tw / 2, cy + th / 2)
        c.drawRoundRect(rf, 5f, 5f, fill)
        stroke.strokeWidth = 1.4f; stroke.color = tileRim
        c.drawRoundRect(rf, 5f, 5f, stroke)
        drawFace(c, cx, cy, tw, th, face, free)
    }

    private fun drawFace(c: Canvas, cx: Float, cy: Float, tw: Float, th: Float, face: Int, free: Boolean) {
        val a = if (free) 255 else 150
        fun col(c0: Int) = Color.argb(a, Color.red(c0), Color.green(c0), Color.blue(c0))
        val big = th * 0.5f
        when (face) {
            in 0..26 -> {
                val suit = face / 9
                val rank = face % 9 + 1
                text(c, rank.toString(), cx, cy + big * 0.32f, big, col(suitColors[suit]))
                drawSuitMark(c, cx, cy - th * 0.28f, tw * 0.16f, suit, a)
            }
            in 27..30 -> text(c, "ESWN"[face - 27].toString(), cx, cy + big * 0.32f, big, col(slate))
            31 -> text(c, "R", cx, cy + big * 0.32f, big, col(suitColors[2]))
            32 -> text(c, "G", cx, cy + big * 0.32f, big, col(suitColors[1]))
            33 -> {
                stroke.strokeWidth = 2.5f; stroke.color = col(0xFF3F86FF.toInt())
                rf.set(cx - tw * 0.22f, cy - th * 0.24f, cx + tw * 0.22f, cy + th * 0.24f)
                c.drawRoundRect(rf, 3f, 3f, stroke)
            }
            in 34..37 -> { drawFlower(c, cx, cy, tw * 0.26f, col(magenta)); tinyIndex(c, cx, cy, th, face - 34 + 1, a) }
            in 38..41 -> { drawLeaf(c, cx, cy, tw * 0.28f, col(teal)); tinyIndex(c, cx, cy, th, face - 38 + 1, a) }
        }
    }

    private fun drawSuitMark(c: Canvas, cx: Float, cy: Float, r: Float, suit: Int, a: Int) {
        fill.shader = null
        fill.color = Color.argb(a, Color.red(suitColors[suit]), Color.green(suitColors[suit]), Color.blue(suitColors[suit]))
        when (suit) {
            0 -> c.drawCircle(cx, cy, r * 0.7f, fill)
            1 -> {
                c.drawRect(cx - r * 0.7f, cy - r, cx - r * 0.2f, cy + r, fill)
                c.drawRect(cx + r * 0.2f, cy - r, cx + r * 0.7f, cy + r, fill)
            }
            else -> { rf.set(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f); c.drawRect(rf, fill) }
        }
    }

    private fun drawFlower(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill.shader = null; fill.color = color
        for (k in 0 until 4) {
            val ang = k * 1.5708f
            c.drawCircle(cx + cos(ang) * r * 0.7f, cy + sin(ang) * r * 0.7f, r * 0.55f, fill)
        }
        fill.color = Color.argb(Color.alpha(color), 255, 240, 180)
        c.drawCircle(cx, cy, r * 0.4f, fill)
    }

    private fun drawLeaf(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fill.shader = null; fill.color = color
        rf.set(cx - r * 0.6f, cy - r, cx + r * 0.6f, cy + r)
        c.save(); c.rotate(45f, cx, cy); c.drawOval(rf, fill); c.restore()
    }

    private fun tinyIndex(c: Canvas, cx: Float, cy: Float, th: Float, idx: Int, a: Int) {
        text(c, idx.toString(), cx, cy + th * 0.40f, th * 0.2f, Color.argb((a * 0.7f).toInt(), 90, 80, 60))
    }

    // --------------------------------------------------------------- HUD

    private fun drawHud(c: Canvas) {
        text(c, "TILES", 40f, 26f, 11f, Color.argb(255, 150, 200, 245), Paint.Align.LEFT)
        text(c, "${engine.board.remaining()}", 40f, 50f, 22f, Color.WHITE, Paint.Align.LEFT, glow = Color.argb(140, 80, 150, 255))

        if (store.timerOn) {
            text(c, "TIME", 600f, 26f, 11f, Color.argb(255, 150, 200, 245))
            val sec = engine.elapsedSec
            text(c, "%d:%02d".format(sec / 60, sec % 60), 600f, 50f, 20f, Color.WHITE)
            val best = store.bestTime(engine.layout.ordinal)
            if (best > 0) text(c, "best %d:%02d".format(best / 60, best % 60), 600f, 68f, 10f, Color.argb(220, 156, 255, 176))
        }
        text(c, engine.layout.label.uppercase(), W / 2f, 26f, 12f, Color.argb(220, 180, 210, 240))
    }

    // -------------------------------------------------------- overlays

    private fun drawInvalid(c: Canvas) {
        val msg = engine.invalidMsg ?: return
        val a = (engine.invalidT / 2.4f).coerceIn(0f, 1f)
        val alpha = (min(1f, a * 3f) * 255).toInt()
        val hint = engine.invalidIsHint
        rf.set(150f, 438f, 490f, 468f)
        fill.shader = null
        fill.color = if (hint) Color.argb((alpha * 0.85f).toInt(), 54, 44, 16) else Color.argb((alpha * 0.85f).toInt(), 60, 20, 24)
        c.drawRoundRect(rf, 12f, 12f, fill)
        stroke.strokeWidth = 2f
        stroke.color = if (hint) Color.argb((alpha * 0.9f).toInt(), 255, 200, 110) else Color.argb((alpha * 0.9f).toInt(), 255, 110, 110)
        c.drawRoundRect(rf, 12f, 12f, stroke)
        text(c, msg, 320f, 458f, 12.5f, if (hint) Color.argb(alpha, 255, 230, 180) else Color.argb(alpha, 255, 220, 210))
    }

    private fun drawWon(c: Canvas) {
        dim(c, 150)
        panel(c, 130f, 170f, 510f, 320f)
        text(c, "SOLVED!", 320f, 220f, 32f, Color.argb(255, 150, 235, 170), glow = Color.argb(150, 40, 160, 80))
        text(c, engine.resultMsg, 320f, 256f, 14f, Color.argb(255, 210, 224, 245))
        text(c, "TAP FOR MENU", 320f, 298f, 15f, Color.argb((170 + 85 * sin(engine.time * 4f)).toInt().coerceIn(60, 255), 200, 230, 255))
    }

    private fun drawStuck(c: Canvas) {
        dim(c, 120)
        panel(c, 150f, 190f, 490f, 300f)
        text(c, "NO MOVES LEFT", 320f, 230f, 24f, Color.argb(255, 255, 180, 90), glow = Color.argb(140, 160, 90, 20))
        text(c, "TAP TO SHUFFLE", 320f, 270f, 15f, Color.argb((170 + 85 * sin(engine.time * 4f)).toInt().coerceIn(60, 255), 255, 224, 140))
    }

    private fun drawMenu(c: Canvas) {
        val tw = 40f; val th = 52f
        drawTile(c, 288f, 150f, tw, th, 9, true)
        drawTile(c, 320f, 146f, tw, th, 31, true)
        drawTile(c, 352f, 150f, tw, th, 34, true)

        textP.setShadowLayer(16f, 0f, 0f, Color.argb(180, 60, 150, 255))
        text(c, "TAPMAHJONG", 320f, 232f, 40f, 0xFFEAF3FF.toInt())
        textP.clearShadowLayer()
        text(c, "solitaire · match free tiles to clear the board", 320f, 258f, 12f, Color.argb(220, 150, 190, 235))

        val d = Layout.from(engine.menuDiff)
        text(c, "‹  ${d.label}  ›", 320f, 308f, 22f, Color.WHITE, glow = Color.argb(140, 80, 150, 255))
        val best = store.bestTime(engine.menuDiff)
        if (best > 0) text(c, "best time  %d:%02d".format(best / 60, best % 60), 320f, 334f, 12f, Color.argb(220, 156, 255, 176))
        text(c, "cleared   ${store.wins}", 320f, 356f, 12f, Color.argb(220, 255, 224, 120))

        text(c, "swipe ↔ layout   •   tap to play", 320f, 400f, 13f, Color.argb((170 + 85 * sin(engine.time * 3f)).toInt().coerceIn(60, 255), 200, 230, 255))
        text(c, "double-tap for settings · Hint & Shuffle inside", 320f, 422f, 11f, Color.argb(160, 150, 175, 210))
    }

    // --------------------------------------------------------- settings

    private fun drawSettings(c: Canvas) {
        dim(c, 188)
        panel(c, 138f, 34f, 502f, 446f)
        text(c, "SETTINGS", 320f, 64f, 20f, Color.WHITE, glow = Color.argb(160, 80, 150, 255))
        val menu = engine.settingsMenu
        val visible = 10
        val start = (menu.selected - visible / 2).coerceIn(0, (menu.items.size - visible).coerceAtLeast(0))
        var y = 96f
        for (i in start until min(start + visible, menu.items.size)) {
            val item = menu.items[i]
            val sel = i == menu.selected
            if (sel) {
                fill.shader = null; fill.color = Color.argb(210, 36, 64, 106)
                rf.set(150f, y - 15f, 490f, y + 8f)
                c.drawRoundRect(rf, 8f, 8f, fill)
            }
            text(c, item.label, 166f, y, 13f, if (sel) Color.WHITE else Color.argb(255, 159, 180, 208), Paint.Align.LEFT)
            val v = item.value()
            if (v.isNotEmpty()) {
                val shown = if (sel && item.adjust != null) "‹ $v ›" else v
                text(c, shown, 474f, y, 13f, if (sel) Color.argb(255, 255, 224, 128) else Color.argb(255, 120, 144, 176), Paint.Align.RIGHT)
            }
            y += 33f
        }
        if (start > 0) text(c, "▲", 320f, 86f, 10f, Color.argb(180, 150, 180, 220))
        if (start + visible < menu.items.size) text(c, "▼", 320f, 430f, 10f, Color.argb(180, 150, 180, 220))
        text(c, "swipe ↕ select   ↔ adjust   tap OK   double-tap close", 320f, 462f, 10.5f, Color.argb(200, 150, 175, 210))
    }

    // ------------------------------------------------------ fx & helpers

    private fun drawParticles(c: Canvas) {
        for (pt in engine.particles.list) {
            val k = (pt.life / pt.maxLife).coerceIn(0f, 1f)
            val alpha = (k * 255).toInt()
            if (pt.ring) {
                stroke.strokeWidth = 1.5f + 3f * k; stroke.color = pt.color; stroke.alpha = alpha
                c.drawCircle(pt.x, pt.y, pt.size * (1f + (1f - k) * 2f), stroke)
            } else {
                fill.shader = null; fill.color = pt.color; fill.alpha = alpha
                c.drawCircle(pt.x, pt.y, pt.size * (0.4f + 0.6f * k), fill)
            }
        }
        stroke.alpha = 255; fill.alpha = 255
    }

    private fun dim(c: Canvas, a: Int) {
        fill.shader = null; fill.color = Color.argb(a, 0, 0, 0)
        c.drawRect(0f, 0f, W, H, fill)
    }

    private fun panel(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        rf.set(l, t, r, b)
        fill.shader = null; fill.color = Color.argb(236, 12, 22, 42)
        c.drawRoundRect(rf, 16f, 16f, fill)
        stroke.strokeWidth = 2f; stroke.color = Color.argb(200, 95, 134, 200)
        c.drawRoundRect(rf, 16f, 16f, stroke)
    }

    private fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.CENTER, glow: Int = 0,
    ) {
        textP.textSize = size
        textP.textAlign = align
        textP.color = color
        if (glow != 0) textP.setShadowLayer(size * 0.4f, 0f, 0f, glow) else textP.clearShadowLayer()
        c.drawText(s, x, y, textP)
        textP.clearShadowLayer()
    }
}
