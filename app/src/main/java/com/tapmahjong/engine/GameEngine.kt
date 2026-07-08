package com.tapmahjong.engine

import com.tapmahjong.SettingsStore
import com.tapmahjong.audio.Audio
import kotlin.math.abs
import kotlin.random.Random

enum class GameState { MENU, PLAYING, WON, STUCK }

interface GameHost {
    fun applySettings()
    fun sound(id: Int, pitch: Float = 1f, vol: Float = 1f)
}

/**
 * Mahjong solitaire: pick two free, matching tiles to clear them; clear the
 * board to win. Single player — no AI. Keeps the suite's swipe-cursor +
 * dwell-to-commit input, autosave/resume, and settings conventions.
 */
class GameEngine(val store: SettingsStore, val host: GameHost) {

    var state = GameState.MENU
        private set
    var settingsOpen = false
        private set
    val settingsMenu = SettingsMenu(this, store)

    val particles = ParticleSystem()

    var layout = Layout.MEDIUM
        private set
    var board = Board(Layouts.build(Layout.MEDIUM))
        private set

    var cursor = 0
    var selected = -1
        private set
    var hintPair: Pair<Int, Int>? = null
        private set
    private var hintT = 0f

    var cursorSince = 0f
        private set
    val dwellProgress get() = ((time - cursorSince) / DWELL).coerceIn(0f, 1f)
    val moveArmed get() = time - cursorSince >= DWELL

    var invalidMsg: String? = null
        private set
    var invalidIsHint = false
        private set
    var invalidT = 0f
    var statusMsg = ""
        private set
    var resultMsg = ""
        private set

    var elapsedMs = 0L
        private set
    val elapsedSec get() = (elapsedMs / 1000).toInt()

    var time = 0f
    var menuDiff = 1

    private val history = ArrayList<Pair<Int, Int>>()
    private var rng = Random(System.nanoTime())

    // Tile geometry (computed to fit each layout).
    private var halfW = 18f
    private var halfH = 22f
    private var loff = 3f
    private var originX = 0f
    private var originY = 0f
    var drawOrder: IntArray = IntArray(0)
        private set

    fun boot() {
        menuDiff = store.difficulty
        val saved = store.savedGame
        if (saved != null && runCatching { resume(saved) }.getOrDefault(false)) return
        toMenu()
    }

    // ------------------------------------------------------------- loop

    fun update(dt: Float) {
        time += dt
        particles.update(dt)
        if (invalidT > 0f) { invalidT -= dt; if (invalidT <= 0f) invalidMsg = null }
        if (hintT > 0f) { hintT -= dt; if (hintT <= 0f) hintPair = null }
        if (settingsOpen) return
        if (state == GameState.PLAYING && store.timerOn) elapsedMs += (dt * 1000).toLong()
    }

    // ------------------------------------------------------------- input

    fun cursorMove(dir: Int) {
        if (state != GameState.PLAYING && state != GameState.STUCK) return
        val cx = tileCX(cursor); val cy = tileCY(cursor)
        var best = -1; var bestScore = Float.MAX_VALUE
        for (j in 0 until board.n) {
            if (!board.present[j] || j == cursor) continue
            val dx = tileCX(j) - cx; val dy = tileCY(j) - cy
            val inDir = when (dir) { 0 -> dy < -2f; 1 -> dy > 2f; 2 -> dx < -2f; else -> dx > 2f }
            if (!inDir) continue
            val prim = if (dir <= 1) abs(dy) else abs(dx)
            val perp = if (dir <= 1) abs(dx) else abs(dy)
            val score = prim + perp * 2f
            if (score < bestScore) { bestScore = score; best = j }
        }
        if (best >= 0) { cursor = best; cursorSince = time; host.sound(Audio.TICK, 1.4f, 0.5f) }
    }

    fun click() {
        when {
            settingsOpen -> settingsMenu.activate()
            state == GameState.MENU -> startGame()
            state == GameState.WON -> toMenu()
            state == GameState.STUCK -> shuffle()
            state == GameState.PLAYING -> boardClick()
        }
    }

    private fun boardClick() {
        val c = cursor
        if (!board.present[c]) return
        if (!board.isFree(c)) {
            setInvalid(board.explainTile(c))
            host.sound(Audio.ILLEGAL)
            return
        }
        if (selected == -1) { select(c); return }
        if (c == selected) { deselect(); return }
        if (board.canMatch(selected, c)) {
            if (!moveArmed) {
                setHint("Rest on the tile a moment, then tap to match.")
                host.sound(Audio.TICK, 0.9f, 0.6f)
                return
            }
            match(selected, c)
        } else {
            // Free tile but not a match — reselect it.
            setHint("Not a match — tiles must be the same.")
            select(c)
        }
    }

    private fun select(i: Int) {
        selected = i
        invalidMsg = null
        cursorSince = time
        host.sound(Audio.SELECT)
    }

    private fun deselect() {
        selected = -1
    }

    private fun match(a: Int, b: Int) {
        history.add(a to b)
        board.remove(a, b)
        particles.burst(tileCX(a), tileCY(a), 0xFFFFE070.toInt(), 1f)
        particles.burst(tileCX(b), tileCY(b), 0xFFFFE070.toInt(), 1f)
        host.sound(Audio.CAPTURE)
        deselect()
        hintPair = null
        if (!board.present[cursor]) cursor = nearestPresent(a)
        cursorSince = time
        evaluate()
        if (state != GameState.WON && state != GameState.STUCK) persist()
    }

    private fun evaluate() {
        if (board.remaining() == 0) {
            state = GameState.WON
            resultMsg = "Board cleared in ${fmt(elapsedSec)}!"
            store.wins++
            val best = store.bestTime(layout.ordinal)
            if (store.timerOn && elapsedSec > 0 && (best == 0 || elapsedSec < best)) store.setBestTime(layout.ordinal, elapsedSec)
            store.clearSavedGame()
            host.sound(Audio.WIN)
        } else if (!board.hasMove()) {
            state = GameState.STUCK
            statusMsg = "No moves left"
            host.sound(Audio.LOSE)
        } else {
            statusMsg = ""
        }
    }

    private fun nearestPresent(from: Int): Int {
        val fx = tileCX(from); val fy = tileCY(from)
        var best = cursor; var bestD = Float.MAX_VALUE
        for (j in 0 until board.n) {
            if (!board.present[j]) continue
            val d = abs(tileCX(j) - fx) + abs(tileCY(j) - fy)
            if (d < bestD) { bestD = d; best = j }
        }
        return best
    }

    // --------------------------------------------------------- actions

    fun hint() {
        val m = board.findMatch()
        if (m != null) {
            hintPair = m; hintT = 2.5f
            cursor = m.first; cursorSince = time
            host.sound(Audio.SELECT, 1.2f)
        } else {
            statusMsg = "No moves left"
            host.sound(Audio.ILLEGAL)
        }
    }

    fun shuffle() {
        if (state != GameState.PLAYING && state != GameState.STUCK) return
        board.reshuffle(rng)
        deselect(); hintPair = null
        state = GameState.PLAYING
        statusMsg = if (board.hasMove()) "" else "No moves left"
        if (statusMsg.isNotEmpty()) state = GameState.STUCK
        host.sound(Audio.MOVE)
        persist()
    }

    fun undo() {
        if (history.isEmpty()) return
        val (a, b) = history.removeAt(history.size - 1)
        board.restore(a, b)
        deselect(); hintPair = null
        state = GameState.PLAYING
        statusMsg = ""
        cursor = a; cursorSince = time
        host.sound(Audio.TICK)
        persist()
    }

    // ------------------------------------------------------- navigation

    fun startGame() {
        layout = Layout.from(store.difficulty)
        rng = Random(System.nanoTime())
        startDeal()
        history.clear()
        deselect(); hintPair = null
        elapsedMs = 0L
        statusMsg = ""; resultMsg = ""; invalidMsg = null
        particles.clear()
        settingsOpen = false
        store.clearSavedGame()
        state = GameState.PLAYING
        host.sound(Audio.SELECT)
        persist()
    }

    private fun startDeal() {
        val slots = Layouts.build(layout)
        board = Board(slots)
        if (!board.deal(rng)) board.deal(rng) // one retry; deal already retries internally
        computeGeometry(slots)
        cursor = board.freeSlots().firstOrNull() ?: 0
        cursorSince = time
    }

    private fun computeGeometry(slots: List<Slot>) {
        var minGx = Int.MAX_VALUE; var maxGx = Int.MIN_VALUE
        var minGy = Int.MAX_VALUE; var maxGy = Int.MIN_VALUE
        for (s in slots) {
            minGx = minOf(minGx, s.gx); maxGx = maxOf(maxGx, s.gx + 2)
            minGy = minOf(minGy, s.gy); maxGy = maxOf(maxGy, s.gy + 2)
        }
        val spanGX = (maxGx - minGx).toFloat().coerceAtLeast(2f)
        val spanGY = (maxGy - minGy).toFloat().coerceAtLeast(2f)
        halfW = (470f / spanGX).coerceIn(9f, 24f)
        halfH = (300f / spanGY).coerceIn(11f, 30f)
        loff = halfW * 0.18f
        originX = 300f - spanGX * halfW / 2f - minGx * halfW
        originY = 215f - spanGY * halfH / 2f - minGy * halfH
        drawOrder = slots.indices.sortedWith(
            compareBy({ slots[it].layer }, { slots[it].gy }, { slots[it].gx })
        ).toIntArray()
    }

    fun toMenu() {
        state = GameState.MENU
        settingsOpen = false
        particles.clear()
        menuDiff = store.difficulty
    }

    fun newGame() = startGame()

    // -------------------------------------------------------- settings/UI

    fun doubleTap() {
        settingsOpen = !settingsOpen
        if (settingsOpen) settingsMenu.onOpen()
        host.sound(if (settingsOpen) Audio.SELECT else Audio.TICK)
    }

    fun onBack(): Boolean {
        if (settingsOpen) { doubleTap(); return true }
        return when (state) {
            GameState.PLAYING, GameState.STUCK -> { doubleTap(); true }
            GameState.WON -> { toMenu(); true }
            else -> false
        }
    }

    fun swipeDir(dir: Int) {
        if (settingsOpen) { settingsMenu.onDir(dir); return }
        when (state) {
            GameState.PLAYING, GameState.STUCK -> cursorMove(dir)
            GameState.MENU -> if (dir == 2 || dir == 3) {
                menuDiff = (menuDiff + (if (dir == 3) 1 else -1)).coerceIn(0, 2)
                store.difficulty = menuDiff
                host.sound(Audio.TICK)
            }
            else -> {}
        }
    }

    fun onAppPause() {
        persist()
        if ((state == GameState.PLAYING || state == GameState.STUCK) && !settingsOpen) {
            settingsOpen = true
            settingsMenu.onOpen()
        }
    }

    private fun setInvalid(msg: String) { invalidMsg = msg; invalidIsHint = false; invalidT = 2.4f }
    private fun setHint(msg: String) { invalidMsg = msg; invalidIsHint = true; invalidT = 1.4f }

    private fun fmt(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

    // ------------------------------------------------------- persistence

    private fun serialize(): String {
        val sb = StringBuilder("1|")
        sb.append(layout.ordinal).append("|")
        sb.append(board.face.joinToString(",")).append("|")
        sb.append((0 until board.n).joinToString("") { if (board.present[it]) "1" else "0" }).append("|")
        sb.append(elapsedMs)
        return sb.toString()
    }

    private fun resume(s: String): Boolean {
        val parts = s.split("|")
        if (parts.size < 5 || parts[0] != "1") return false
        layout = Layout.from(parts[1].toInt())
        val slots = Layouts.build(layout)
        val faces = parts[2].split(",").map { it.toInt() }
        if (faces.size != slots.size) return false
        val pres = parts[3]
        if (pres.length != slots.size) return false
        board = Board(slots)
        for (i in slots.indices) { board.face[i] = faces[i]; board.present[i] = pres[i] == '1' }
        elapsedMs = parts[4].toLong()
        computeGeometry(slots)
        history.clear()
        deselect(); hintPair = null
        invalidMsg = null; resultMsg = ""
        rng = Random(System.nanoTime())
        cursor = board.freeSlots().firstOrNull() ?: (0 until board.n).firstOrNull { board.present[it] } ?: 0
        cursorSince = time
        state = if (board.remaining() == 0) GameState.MENU else GameState.PLAYING
        statusMsg = if (state == GameState.PLAYING && !board.hasMove()) { state = GameState.STUCK; "No moves left" } else ""
        return state != GameState.MENU
    }

    private fun persist() {
        if (state == GameState.PLAYING || state == GameState.STUCK) store.savedGame = serialize()
    }

    // --------------------------------------------------- tile geometry

    companion object {
        const val DWELL = 0.7f
    }

    fun tileCX(i: Int) = originX + (board.slots[i].gx + 1) * halfW - board.slots[i].layer * loff
    fun tileCY(i: Int) = originY + (board.slots[i].gy + 1) * halfH - board.slots[i].layer * loff
    fun tileW() = 2 * halfW
    fun tileH() = 2 * halfH
    fun layerOffset() = loff
}
