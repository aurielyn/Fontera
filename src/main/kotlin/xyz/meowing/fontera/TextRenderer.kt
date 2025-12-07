package xyz.meowing.fontera

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import xyz.meowing.fontera.Fontera.client
import xyz.meowing.fontera.utils.FormatParser
import xyz.meowing.fontera.utils.ImageFactory
import xyz.meowing.fontera.utils.TextLayoutEngine
import xyz.meowing.fontera.utils.TextureManager
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

class TextRenderer(
    fontPath: String? = null,
    private val baseSize: Float = 10f
) {
    private val textureCache = mutableMapOf<TextureCacheKey, CachedTexture>()
    private var lastCleanup = System.currentTimeMillis()

    private var renderSize: Float = baseSize
    private var primaryFont: Font? = null
    private var monospaceFont: Font? = null
    private var fallbackFont: Font? = null

    private val loadedFont: Font = fontPath?.let(::loadCustomFont)
        ?: GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts.first()

    private fun loadCustomFont(path: String): Font {
        return runCatching {
            Font.createFont(Font.TRUETYPE_FONT, javaClass.getResourceAsStream(path))
        }.getOrElse {
            GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts[0]
        }
    }

    fun render(
        graphics: GuiGraphics,
        text: String,
        x: Float,
        y: Float,
        scale: Float = 1f,
        shadow: Boolean = true,
        background: Background = Background.None,
        align: Alignment = Alignment.Left
    ) {
        renderInternal(graphics, listOf(text), x, y, scale, shadow, background, align)
    }

    fun renderLines(
        graphics: GuiGraphics,
        lines: List<String>,
        x: Float,
        y: Float,
        scale: Float = 1f,
        shadow: Boolean = true,
        background: Background = Background.None,
        align: Alignment = Alignment.Left
    ) {
        renderInternal(graphics, lines, x, y, scale, shadow, background, align)
    }

    private fun renderInternal(
        graphics: GuiGraphics,
        lines: List<String>,
        x: Float,
        y: Float,
        scale: Float,
        shadow: Boolean,
        background: Background,
        align: Alignment
    ) {
        val window = client.window
        val targetSize = baseSize * window.guiScale.toFloat() * scale
        val targetGuiScale = 1f / window.guiScale.toFloat()

        if (targetSize != renderSize || primaryFont == null) updateFonts(targetSize)

        val cacheKey = TextureCacheKey(lines, shadow, background, align)
        val cached = textureCache.getOrPut(cacheKey) {
            createCachedTexture(lines, shadow, background, align)
        }

        cached.lastUsed = System.currentTimeMillis()
        cleanupCache()

        if (client.options.hideGui || cached.textureManager.textureId == -1) return

        val position = when (align) {
            Alignment.Left -> x to y
            Alignment.Right -> (x - cached.maxTextWidth * targetGuiScale) to y
            Alignment.Center -> (x - cached.maxTextWidth * targetGuiScale * 0.5f) to y
        }

        graphics.drawTexture(
            cached.textureId,
            position.first.toInt(),
            position.second.toInt(),
            (cached.textureManager.width * targetGuiScale).toInt(),
            (cached.textureManager.height * targetGuiScale).toInt(),
            0f, 0f, 1f, 1f, -1
        )
    }

    private fun updateFonts(targetSize: Float) {
        renderSize = targetSize
        primaryFont = loadedFont.deriveFont(Font.PLAIN, renderSize)
        monospaceFont = Font(Font.MONOSPACED, Font.PLAIN, (renderSize + 0.5f).toInt())
        fallbackFont = Font(Font.SANS_SERIF, Font.PLAIN, (renderSize + 0.5f).toInt())
    }

    private fun createCachedTexture(
        lines: List<String>,
        shadow: Boolean,
        background: Background,
        align: Alignment
    ): CachedTexture {
        val texMgr = TextureManager("cache_${nextId.getAndIncrement()}")
        val texId = ResourceLocation.fromNamespaceAndPath("fontera", "render/${texMgr.id.lowercase()}")
        texMgr.register(texId)

        val tempLines = lines.map { CachedTextLine(it, dirty = true) }
        val graphics2D = ImageFactory.createGraphics()
        graphics2D.font = primaryFont

        var maxTextWidth = 0f
        var maxVisibleWidth = 0f

        tempLines.forEach { line ->
            line.layout = FormatParser.parse(
                line.text, shadow, graphics2D,
                primaryFont!!, monospaceFont!!, fallbackFont!!, renderSize
            )
            line.layout?.let { layout ->
                maxTextWidth = max(maxTextWidth, layout.totalWidth)
                maxVisibleWidth = max(maxVisibleWidth, layout.visibleWidth)
            }
        }
        graphics2D.dispose()

        val shadowOffset = if (shadow) renderSize * 0.1 else 0.0
        val width = ceil(maxTextWidth + shadowOffset).toInt()
        val height = ceil(renderSize * (tempLines.size + 1) + shadowOffset).toInt()

        val textureWidth = nextPowerOfTwo(width, 1)
        val textureHeight = nextPowerOfTwo(height, 2)
        val image = ImageFactory.createImage(textureWidth, textureHeight)

        TextLayoutEngine.render(
            image,
            RenderConfig(align, shadow, background, renderSize, primaryFont!!, tempLines.mapNotNull { it.layout }, maxVisibleWidth)
        )

        texMgr.upload(image)

        return CachedTexture(texMgr, texId, maxTextWidth)
    }

    private fun cleanupCache() {
        val now = System.currentTimeMillis()
        if (now - lastCleanup < 60000L) return

        lastCleanup = now

        if (textureCache.size > 20) {
            val sorted = textureCache.entries.sortedBy { it.value.lastUsed }
            val toRemove = sorted.take(textureCache.size - 15)
            toRemove.forEach { textureCache.remove(it.key) }
        }
    }

    private fun nextPowerOfTwo(value: Int, shift: Int): Int {
        val bits = max(0, 31 - Integer.numberOfLeadingZeros(value) - shift)
        val mask = (1 shl bits) - 1
        return (value + mask) and mask.inv()
    }

    fun dispose() {
        textureCache.clear()
    }

    //#if MC >= 1.21.8
    //$$ fun GuiGraphics.drawTexture(texture: ResourceLocation, x: Int, y: Int, width: Int, height: Int, u0: Float, v0: Float, u1: Float, v1: Float, color: Int) {
    //$$    val minx = x
    //$$    val miny = y
    //$$    val maxx = (x + width)
    //$$    val maxy = (y + height)
    //$$
    //$$    this.guiRenderState.submitGuiElement(
    //$$        net.minecraft.client.gui.render.state.BlitRenderState(
    //$$            net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
    //$$            net.minecraft.client.gui.render.TextureSetup.singleTexture(client.textureManager.getTexture(texture).textureView),
    //$$            org.joml.Matrix3x2f(this.pose()),
    //$$            minx, miny, maxx, maxy, u0, u1, v0, v1, color,
    //$$            this.scissorStack.peek(),
    //$$        ),
    //$$    )
    //$$ }
    //#else
    fun GuiGraphics.drawTexture(texture: ResourceLocation, x: Int, y: Int, width: Int, height: Int, u0: Float, v0: Float, u1: Float, v1: Float, color: Int) {
        val matrix = this.pose().last().pose()
        val minx = x.toFloat()
        val miny = y.toFloat()
        val maxx = (x + width).toFloat()
        val maxy = (y + height).toFloat()

        this.drawSpecial { source ->
            val buffer = source.getBuffer(RenderType.guiTextured(texture))
            buffer.addVertex(matrix, minx, miny, 0f).setColor(color).setUv(u0, v0)
            buffer.addVertex(matrix, minx, maxy, 0f).setColor(color).setUv(u0, v1)
            buffer.addVertex(matrix, maxx, maxy, 0f).setColor(color).setUv(u1, v1)
            buffer.addVertex(matrix, maxx, miny, 0f).setColor(color).setUv(u1, v0)
        }
    }
    //#endif

    private data class CachedTextLine(
        val text: String,
        var layout: FormatParser.ParsedTextLayout? = null,
        var dirty: Boolean = true
    )

    private data class TextureCacheKey(
        val lines: List<String>,
        val shadow: Boolean,
        val background: Background,
        val align: Alignment
    )

    private data class CachedTexture(
        val textureManager: TextureManager,
        val textureId: ResourceLocation,
        val maxTextWidth: Float,
        var lastUsed: Long = System.currentTimeMillis()
    )

    data class RenderConfig(
        val alignment: Alignment,
        val shadow: Boolean,
        val background: Background,
        val fontSize: Float,
        val font: Font,
        val layouts: List<FormatParser.ParsedTextLayout>,
        val maxVisibleWidth: Float
    )

    enum class Alignment {
        Left,
        Right,
        Center
        ;
    }

    enum class Background {
        None,
        Full,
        PerLine
        ;
    }

    companion object {
        private val nextId = AtomicInteger(0)
        private val instances = mutableMapOf<String, TextRenderer>()
        private val usage = mutableMapOf<String, Long>()
        private var lastCleanup = 0L

        @JvmStatic
        @JvmOverloads
        fun render(
            graphics: GuiGraphics,
            text: String,
            x: Float,
            y: Float,
            fontPath: String? = null,
            baseSize: Float = 10f,
            scale: Float = 1f,
            shadow: Boolean = true,
            background: Background = Background.None,
            align: Alignment = Alignment.Left
        ) {
            val key = "${fontPath ?: "default"}_$baseSize"
            val renderer = instances.getOrPut(key) { TextRenderer(fontPath, baseSize) }
            usage[key] = System.currentTimeMillis()
            renderer.render(graphics, text, x, y, scale, shadow, background, align)
            cleanup()
        }

        @JvmStatic
        @JvmOverloads
        fun renderLines(
            graphics: GuiGraphics,
            lines: List<String>,
            x: Float,
            y: Float,
            fontPath: String? = null,
            baseSize: Float = 10f,
            scale: Float = 1f,
            shadow: Boolean = true,
            background: Background = Background.None,
            align: Alignment = Alignment.Left
        ) {
            val key = "${fontPath ?: "default"}_$baseSize"
            val renderer = instances.getOrPut(key) { TextRenderer(fontPath, baseSize) }
            usage[key] = System.currentTimeMillis()
            renderer.renderLines(graphics, lines, x, y, scale, shadow, background, align)
            cleanup()
        }

        private fun cleanup() {
            val now = System.currentTimeMillis()
            if (now - lastCleanup < 60000L) return

            lastCleanup = now

            usage
                .filter { now - it.value > 300000L }
                .keys
                .forEach { key ->
                    instances.remove(key)?.dispose()
                    usage.remove(key)
                }
        }
    }
}