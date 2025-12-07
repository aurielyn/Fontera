package xyz.meowing.fontera.utils

import xyz.meowing.fontera.TextRenderer
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.math.ceil

object TextLayoutEngine {
    private const val SHADOW_OFFSET_FACTOR = 0.1f
    private val SHADOW_COLOR = Color(63, 63, 63, 255)
    private val BACKGROUND_COLOR = Color(0, 0, 0, 64)
    private val TEXT_COLOR = Color.WHITE

    fun render(image: BufferedImage, config: TextRenderer.RenderConfig): BufferedImage {
        val graphics = image.createGraphics()
        graphics.font = config.font
        val baseline = graphics.fontMetrics.ascent
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        if (config.background == TextRenderer.Background.Full) drawFullBackground(graphics, config)

        config.layouts.forEachIndexed { index, layout ->
            val yPos = index * config.fontSize + baseline
            val xPos = calculateXPosition(config.alignment, config.maxVisibleWidth, layout.visibleWidth)

            if (config.background == TextRenderer.Background.PerLine) drawLineBackground(graphics, xPos, yPos, baseline, layout.visibleWidth, config.fontSize)
            if (config.shadow) drawShadow(graphics, layout, xPos, yPos, config.fontSize)

            drawText(graphics, layout, xPos, yPos)
        }

        graphics.dispose()
        return image
    }

    private fun drawFullBackground(graphics: Graphics2D, config: TextRenderer.RenderConfig) {
        graphics.paint = BACKGROUND_COLOR
        graphics.fillRect(
            0, 0,
            (config.maxVisibleWidth + 0.5f).toInt(),
            (config.layouts.size * config.fontSize + 0.5f).toInt()
        )
    }

    private fun drawLineBackground(
        graphics: Graphics2D,
        xPos: Float,
        yPos: Float,
        baseline: Int,
        width: Float,
        height: Float
    ) {
        graphics.paint = BACKGROUND_COLOR
        graphics.fillRect(
            xPos.toInt(),
            (yPos - baseline).toInt(),
            ceil(width).toInt(),
            ceil(height).toInt()
        )
    }

    private fun drawShadow(
        graphics: Graphics2D,
        layout: FormatParser.ParsedTextLayout,
        xPos: Float,
        yPos: Float,
        fontSize: Float
    ) {
        val offset = fontSize * SHADOW_OFFSET_FACTOR
        graphics.paint = SHADOW_COLOR
        layout.shadowLayout?.draw(graphics, xPos + offset, yPos + offset)
    }

    private fun drawText(
        graphics: Graphics2D,
        layout: FormatParser.ParsedTextLayout,
        xPos: Float,
        yPos: Float
    ) {
        graphics.paint = TEXT_COLOR
        layout.mainLayout.draw(graphics, xPos, yPos)
    }

    private fun calculateXPosition(alignment: TextRenderer.Alignment, maxWidth: Float, textWidth: Float): Float {
        return when (alignment) {
            TextRenderer.Alignment.Left -> 0f
            TextRenderer.Alignment.Right -> maxWidth - textWidth
            TextRenderer.Alignment.Center -> (maxWidth - textWidth) * 0.5f
        }
    }
}