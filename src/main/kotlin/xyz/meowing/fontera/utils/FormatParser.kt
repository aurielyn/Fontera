package xyz.meowing.fontera.utils

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.text.AttributedCharacterIterator
import java.text.AttributedString
import kotlin.random.Random

object FormatParser {
    private val primaryColors = mapOf(
        '0' to Color(0),
        '1' to Color(170),
        '2' to Color(43520),
        '3' to Color(43690),
        '4' to Color(11141120),
        '5' to Color(11141290),
        '6' to Color(16755200),
        '7' to Color(11184810),
        '8' to Color(5592405),
        '9' to Color(5592575),
        'a' to Color(5635925),
        'b' to Color(5636095),
        'c' to Color(16733525),
        'd' to Color(16733695),
        'e' to Color(16777045),
        'f' to Color(16777215)
    )

    private val shadowColors = mapOf(
        '0' to Color(0),
        '1' to Color(42),
        '2' to Color(10752),
        '3' to Color(10794),
        '4' to Color(2752512),
        '5' to Color(2752554),
        '6' to Color(4139520),
        '7' to Color(2763306),
        '8' to Color(1381653),
        '9' to Color(1381695),
        'a' to Color(1392405),
        'b' to Color(1392447),
        'c' to Color(4134165),
        'd' to Color(4134207),
        'e' to Color(4144917),
        'f' to Color(4144959)
    )

    private val formatCodes = setOf('l', 'o', 'm', 'n')
    private val fontCache = mutableMapOf<Char, Font?>()

    fun parse(
        text: String,
        shadow: Boolean,
        graphics: Graphics2D,
        primary: Font,
        mono: Font,
        fallback: Font,
        size: Float
    ): ParsedTextLayout {
        val processed = "$text&r"
        val content = StringBuilder()
        val animations = mutableListOf<AnimatedSegment>()
        val formats = mutableListOf<FormatSpan>()
        val activeFormats = mutableListOf<FormatSpan>()
        var animStart = -1

        var i = 0
        while (i < processed.length) {
            val char = processed[i]

            if ((char == '&' || char == '§') && i < processed.length - 1) {
                when (val code = processed[i + 1]) {
                    '\u200B' -> {
                        content.append(if (animStart >= 0) ' ' else char)
                        i += 2

                        continue
                    }

                    in primaryColors -> {
                        activeFormats.removeIf {
                            if (it.code in primaryColors) {
                                formats.add(FormatSpan(it.code, it.start, content.length))
                                true
                            } else false
                        }

                        activeFormats.add(FormatSpan(code, content.length, 0))

                        i += 2

                        continue
                    }

                    'k' -> {
                        animStart = content.length
                        i += 2

                        continue
                    }

                    in formatCodes -> {
                        activeFormats.add(FormatSpan(code, content.length, 0))
                        i += 2

                        continue
                    }
                    'r' -> {
                        activeFormats.forEach { formats.add(FormatSpan(it.code, it.start, content.length)) }
                        activeFormats.clear()
                        if (animStart >= 0) animations.add(AnimatedSegment(animStart, content.length))
                        animStart = -1
                        i += 2

                        continue
                    }
                }
            }

            val charToAppend = if (animStart >= 0) randomChar() else char

            content.append(charToAppend)
            i++
        }

        val finalText = content.toString()
        val chars = finalText.toCharArray()
        val mainAttr = AttributedString(finalText)
        val shadowAttr = if (shadow) AttributedString(finalText) else null

        setAttribute(mainAttr, TextAttribute.SIZE, size, 0, finalText.length)
        shadowAttr?.let { setAttribute(it, TextAttribute.SIZE, size, 0, finalText.length) }

        applyFontsToSegments(mainAttr, shadowAttr, chars, primary, mono, fallback, animations, finalText.length)
        applyFormats(mainAttr, shadowAttr, formats)

        val mainLayout = TextLayout(mainAttr.iterator, graphics.fontRenderContext)
        val shadowLayout = shadowAttr?.let { TextLayout(it.iterator, graphics.fontRenderContext) }

        return ParsedTextLayout(
            animations.toTypedArray(),
            mainLayout.advance,
            mainLayout.visibleAdvance,
            mainLayout.ascent,
            mainLayout.descent,
            mainLayout,
            shadowLayout
        )
    }

    private fun applyFontsToSegments(
        mainAttr: AttributedString,
        shadowAttr: AttributedString?,
        chars: CharArray,
        primary: Font,
        mono: Font,
        fallback: Font,
        animations: List<AnimatedSegment>,
        textLength: Int
    ) {
        var end = 0
        animations.forEach { seg ->
            applyFonts(mainAttr, chars, primary, fallback, end, seg.start)
            applyFonts(shadowAttr, chars, primary, fallback, end, seg.start)
            setAttribute(mainAttr, TextAttribute.FONT, mono, seg.start, seg.end)
            shadowAttr?.let { setAttribute(it, TextAttribute.FONT, mono, seg.start, seg.end) }
            end = seg.end
        }
        applyFonts(mainAttr, chars, primary, fallback, end, textLength)
        applyFonts(shadowAttr, chars, primary, fallback, end, textLength)
    }

    private fun applyFormats(mainAttr: AttributedString, shadowAttr: AttributedString?, formats: List<FormatSpan>) {
        formats.forEach { fmt ->
            when (fmt.code) {
                in primaryColors -> {
                    setAttribute(mainAttr, TextAttribute.FOREGROUND, primaryColors[fmt.code], fmt.start, fmt.end)
                    shadowAttr?.let { setAttribute(it, TextAttribute.FOREGROUND, shadowColors[fmt.code], fmt.start, fmt.end) }
                }

                'l' -> {
                    setAttribute(mainAttr, TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, fmt.start, fmt.end)
                    shadowAttr?.let { setAttribute(it, TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD, fmt.start, fmt.end) }
                }

                'o' -> {
                    setAttribute(mainAttr, TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE, fmt.start, fmt.end)
                    shadowAttr?.let { setAttribute(it, TextAttribute.POSTURE, TextAttribute.POSTURE_OBLIQUE, fmt.start, fmt.end) }
                }

                'm' -> {
                    setAttribute(mainAttr, TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON, fmt.start, fmt.end)
                    shadowAttr?.let { setAttribute(it, TextAttribute.STRIKETHROUGH, TextAttribute.STRIKETHROUGH_ON, fmt.start, fmt.end) }
                }

                'n' -> {
                    setAttribute(mainAttr, TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_LOW_ONE_PIXEL, fmt.start, fmt.end)
                    shadowAttr?.let { setAttribute(it, TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_LOW_ONE_PIXEL, fmt.start, fmt.end) }
                }
            }
        }
    }

    private fun randomChar(): Char {
        val code = Random.nextInt(142) + 33
        return (code + if (code >= 94) 1 else 0).toChar()
    }

    private fun setAttribute(attr: AttributedString?, key: Any, value: Any?, start: Int, end: Int) {
        if (start >= end || attr == null) return
        attr.addAttribute(key as AttributedCharacterIterator.Attribute, value, start, end)
    }

    private fun applyFonts(
        attr: AttributedString?,
        chars: CharArray,
        primary: Font,
        fallback: Font,
        start: Int,
        end: Int
    ) {
        if (attr == null || start >= end || start >= chars.size || end > chars.size) return

        var pos = start
        var nextMissing = primary.canDisplayUpTo(chars, pos, end)

        while (pos < chars.size && nextMissing >= 0) {
            setAttribute(attr, TextAttribute.FONT, primary, pos, nextMissing)
            applyFallback(attr, chars[nextMissing], fallback, nextMissing)
            pos = nextMissing + 1
            nextMissing = primary.canDisplayUpTo(chars, pos, end)
        }
        setAttribute(attr, TextAttribute.FONT, primary, pos, end)
    }

    private fun applyFallback(attr: AttributedString, char: Char, defaultFallback: Font, index: Int) {
        val font = if (defaultFallback.canDisplay(char)) {
            defaultFallback
        } else {
            fontCache.getOrPut(char) {
                GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts
                    .firstOrNull { it.canDisplay(char) }
            } ?: defaultFallback
        }

        setAttribute(attr, TextAttribute.FONT, font, index, index + 1)
    }

    data class ParsedTextLayout(
        val animations: Array<AnimatedSegment>,
        val totalWidth: Float,
        val visibleWidth: Float,
        val ascent: Float,
        val descent: Float,
        val mainLayout: TextLayout,
        val shadowLayout: TextLayout?
    ) {
        val hasAnimation get() = animations.isNotEmpty()

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ParsedTextLayout

            if (!animations.contentEquals(other.animations)) return false
            if (totalWidth != other.totalWidth) return false
            if (visibleWidth != other.visibleWidth) return false
            if (ascent != other.ascent) return false
            if (descent != other.descent) return false

            return true
        }

        override fun hashCode(): Int {
            var result = animations.contentHashCode()
            result = 31 * result + totalWidth.hashCode()
            result = 31 * result + visibleWidth.hashCode()
            result = 31 * result + ascent.hashCode()
            result = 31 * result + descent.hashCode()
            return result
        }
    }

    private data class FormatSpan(val code: Char, val start: Int, val end: Int)

    data class AnimatedSegment(val start: Int, val end: Int)
}