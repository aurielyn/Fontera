package xyz.meowing.fontera.utils

import com.mojang.blaze3d.opengl.GlStateManager
import com.mojang.blaze3d.opengl.GlTexture
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.TextureFormat
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.*
import xyz.meowing.fontera.Fontera
import xyz.meowing.fontera.Fontera.client
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.awt.image.PixelInterleavedSampleModel
import java.nio.IntBuffer

class TextureManager(val id: String) : AbstractTexture() {
    var width = 0
    var height = 0
    var textureId = -1
    private var pboId = -1

    fun register(location: ResourceLocation) = apply {
        client.textureManager.register(location, this)
    }

    fun upload(image: BufferedImage) {
        if (RenderSystem.tryGetDevice() == null) Fontera.onClientStart { uploadInternal(image) } else uploadInternal(image)
    }

    private fun uploadInternal(source: BufferedImage) {
        val imgWidth = source.width
        val imgHeight = source.height
        var img = source

        if (!isValidFormat(img)) {
            val converted = ImageFactory.createImage(imgWidth, imgHeight)
            val graphics = converted.createGraphics()
            graphics.drawImage(img, 0, 0, null)
            graphics.dispose()
            img = converted
        }

        if (textureId == -1) {
            initialize(img)
        } else if (imgWidth != width || imgHeight != height) {
            release()
            initialize(img)
        }

        GlStateManager._bindTexture(textureId)
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, imgWidth)
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0)
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0)
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4)

        val pixels = (img.raster.dataBuffer as DataBufferByte).data
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pboId)

        val buffer = GL15.glMapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, GL15.GL_WRITE_ONLY, imgWidth * imgHeight * 4L, null)
        buffer?.let {
            it.put(pixels)
            GL15.glUnmapBuffer(GL21.GL_PIXEL_UNPACK_BUFFER)
        }

        GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, imgWidth, imgHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0)
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
    }

    private fun initialize(img: BufferedImage) {
        width = img.width
        height = img.height
        texture = RenderSystem.getDevice().createTexture(
            id,
            //#if MC >= 1.21.8
            //$$ 0,
            //#endif
            TextureFormat.RGBA8,
            width,
            height,
            1
            //#if MC >= 1.21.8
            //$$ ,1
            //#endif
        )

        //#if MC >= 1.21.8
        //$$ textureView = RenderSystem.getDevice().createTextureView(texture!!, 0, 1)
        //#endif

        textureId = (texture as GlTexture).glId()

        GlStateManager._bindTexture(textureId)
        GlStateManager._texImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGBA,
            width,
            height,
            0,
            GL11.GL_RGBA,
            GL11.GL_UNSIGNED_BYTE,
            //#if MC >= 1.21.10
            //$$ null as java.nio.ByteBuffer?
            //#else
            null as IntBuffer?
            //#endif
        )

        configureTexture()

        pboId = GlStateManager._glGenBuffers()
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pboId)
        GlStateManager._glBufferData(GL21.GL_PIXEL_UNPACK_BUFFER, width * height * 4L, GL15.GL_STREAM_DRAW)
        GlStateManager._glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0)
    }

    private fun configureTexture() {
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, 0)
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0)
    }

    private fun release() {
        if (textureId == -1) return
        GlStateManager._deleteTexture(textureId)
        if (pboId != -1) GlStateManager._glDeleteBuffers(pboId)
        texture?.close()
        pboId = -1
        textureId = -1
    }

    private fun isValidFormat(img: BufferedImage): Boolean {
        val raster = img.raster
        if (raster.dataBuffer !is DataBufferByte) return false
        val model = raster.sampleModel as? PixelInterleavedSampleModel ?: return false
        return model.bandOffsets.withIndex().all { it.index == it.value }
    }
}