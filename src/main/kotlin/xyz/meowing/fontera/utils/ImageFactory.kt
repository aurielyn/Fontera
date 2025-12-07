package xyz.meowing.fontera.utils

import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ComponentColorModel
import java.awt.image.DataBufferByte
import java.awt.image.Raster
import java.awt.Graphics2D
import java.awt.RenderingHints

object ImageFactory {
    private val COLOR_MODEL = ComponentColorModel(
        ColorSpace.getInstance(ColorSpace.CS_sRGB),
        true,
        false,
        Transparency.TRANSLUCENT,
        DataBufferByte.TYPE_BYTE
    )

    private val BLANK_RASTER = Raster.createInterleavedRaster(
        DataBufferByte.TYPE_BYTE, 1, 1, 4, 4, intArrayOf(0, 1, 2, 3), null
    )

    private val BLANK_IMAGE = BufferedImage(COLOR_MODEL, BLANK_RASTER, false, null)

    fun createImage(width: Int, height: Int): BufferedImage {
        val raster = Raster.createInterleavedRaster(
            DataBufferByte.TYPE_BYTE,
            width,
            height,
            width * 4,
            4,
            intArrayOf(0, 1, 2, 3),
            null
        )

        return BufferedImage(COLOR_MODEL, raster, false, null)
    }

    fun createGraphics(): Graphics2D {
        return BLANK_IMAGE.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        }
    }
}