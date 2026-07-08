package com.tapmahjong.engine

/** Layered tile layouts. Difficulty is layout size (more tiles + more layers). */
enum class Layout(val label: String) {
    EASY("1 · Easy"),
    MEDIUM("2 · Medium"),
    HARD("3 · Hard");

    companion object {
        fun from(i: Int) = entries[i.coerceIn(0, entries.size - 1)]
    }
}

object Layouts {
    private fun block(out: MutableList<Slot>, layer: Int, gx0: Int, gy0: Int, cols: Int, rows: Int) {
        for (r in 0 until rows) for (c in 0 until cols) out.add(Slot(layer, gx0 + 2 * c, gy0 + 2 * r))
    }

    /** Slot positions (half-unit grid; each tile is 2x2). Counts are always even. */
    fun build(layout: Layout): List<Slot> {
        val s = ArrayList<Slot>()
        when (layout) {
            Layout.EASY -> {          // 24 + 8 + 4 = 36
                block(s, 0, 0, 0, 6, 4)
                block(s, 1, 2, 2, 4, 2)
                block(s, 2, 4, 2, 2, 2)
            }
            Layout.MEDIUM -> {        // 48 + 18 + 6 = 72
                block(s, 0, 0, 0, 8, 6)
                block(s, 1, 2, 2, 6, 3)
                block(s, 2, 4, 4, 3, 2)
            }
            Layout.HARD -> {          // 60 + 28 + 12 + 4 = 104
                block(s, 0, 0, 0, 10, 6)
                block(s, 1, 2, 2, 7, 4)
                block(s, 2, 4, 4, 4, 3)
                block(s, 3, 6, 4, 2, 2)
            }
        }
        return s
    }
}
