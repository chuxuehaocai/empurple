package dev.naominet.empurple.utils

import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints

object DesignSystem {
    private val fontFamily by lazy {
        val candidates = listOf("MiSans", "Microsoft YaHei", "Noto Sans CJK SC", "SansSerif")
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        candidates.firstOrNull { it in available } ?: Font.SANS_SERIF
    }

    fun miSansRegular(size: Int): Font = Font(fontFamily, Font.PLAIN, size)
    fun miSansBold(size: Int): Font = Font(fontFamily, Font.BOLD, size)

    fun applyHints(g: Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    }
}
