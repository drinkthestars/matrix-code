import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.TextBlob
import org.jetbrains.skia.TextBlobBuilder
import org.jetbrains.skia.Typeface
import org.jetbrains.skia.makeFromFile
import java.util.*
import org.jetbrains.skia.Color as NativeColor
import org.jetbrains.skia.Font as NativeFont
import org.jetbrains.skia.Paint as NativePaint

private val Random = Random()

// Colors
private val BrightGreen = NativeColor.makeRGB(r = 0, g = 253, b = 32)
private val LightGreen = NativeColor.makeRGB(r = 225, g = 254, b = 233)
private val LighterGreen = NativeColor.makeRGB(r = 187, g = 250, b = 217)
//private val GlyphShadow = NativeColor.makeRGB(r = 122, g = 250, b = 220)

// Glyph ints
private const val GlyphMaxCharInt = 126
private const val AsperandInt = 64
private const val LogoGlyph1 = 96
private const val LogoGlyph2 = 97
private const val BackwardsTwoInt = 50

// Glyphs
private const val MinGlyphCount = 10
private const val DefaultMaxGlyphCount = 50
private const val TextSize = 30
private const val RandomizeCharThresh = 0.003f
//private const val GlyphShadowRadius = TextSize + 14f

// Streams
private const val AdvanceResetMax = 5
private const val HighlightCharCount = 3
private const val StreamSpacingDelta = 15
private const val StartingYOffset = -(10 * TextSize)
private const val FrameRate = 16

// Note: smaller denominator = slower
private const val FastSpeedMin = FrameRate / 4
private const val FastSpeedMax = FrameRate / 2
private const val SlowSpeedMin = FrameRate / 2
private const val SlowSpeedMax = FrameRate * 2

// TODO: Improve this load
// https://www.norfok.com/portfolio-freeware_matrixcode.html
private val GlyphFont = NativeFont(
    typeface = Typeface.makeFromFile(path = "fonts/matrix_code_nfi.ttf"),
    size = TextSize.toFloat()
)

@Composable
fun MatrixCodeRain() {
    val paint = remember { paint() }
    val streams = remember { mutableListOf<Stream>() }
    var width = remember { 0 }
    var advance by remember { mutableStateOf(0) }

    Canvas(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        .onSizeChanged {
            if (width == 0 && it.width != 0) {
                width = it.width
                streams.addStreams(width, it.height)
            }
        },
        onDraw = {
            streams.forEach { it.draw(paint, this, advance) }
            if (advance == AdvanceResetMax) advance = 0 else advance += 1
        }
    )
}

private class Glyph(val xPos: Float, var yPos: Float) {
    var char: Char = randomChar()

    fun randomize() {
        char = randomChar()
    }
}

private class Stream(
    xPos: Int,
    private val height: Int,
    private var speed: Int,
    maxGlyphs: Int = DefaultMaxGlyphCount,
    private val fixedAlpha: Int? = null,
) {

    private var glyphs: ArrayList<Glyph> = ArrayList(maxGlyphs)
    private var glyphCount: Int = randomInt(MinGlyphCount, maxGlyphs)
    private var glyphYDelta: Int = randomInt(0, height - TextSize)
    private var highlightRange: IntRange
    private val glyphsLastIndex: Int

    init {
        var yPos = StartingYOffset
        while (yPos < glyphCount * TextSize) {
            glyphs.add(Glyph(xPos.toFloat(), (yPos + glyphYDelta).toFloat()))
            yPos += TextSize
        }
        highlightRange = (glyphs.size - HighlightCharCount)..glyphs.size
        glyphsLastIndex = glyphs.lastIndex
    }

    fun draw(paint: NativePaint, drawScope: DrawScope, advance: Int) {
        glyphs.forEachIndexed { index, glyph ->
            val alpha = index.map(
                oldMin = 0f,
                oldMax = glyphsLastIndex.toFloat(),
                newMin = 40f,
                newMax = 255f
            )

            val color = if (index in highlightRange) {
                if (index == glyphsLastIndex) LightGreen else LighterGreen
            } else {
                BrightGreen
            }

            if (advance % speed == 0) {
                glyph.yPos += TextSize

                if (index == glyphsLastIndex) {
                    glyph.randomize()
                } else {
                    glyph.char = glyphs[index + 1].char
                }
            }

            if (Random.nextFloat() < RandomizeCharThresh) glyph.randomize()

            glyph.draw(
                paint = paint,
                drawScope = drawScope,
                color = color,
                dynamicAlpha = alpha.toInt(),
                fixedAlpha = fixedAlpha
            )
        }

        resetYPos()
    }

    private fun resetYPos() {
        if (glyphs[0].yPos > height) {
            glyphs.forEachIndexed { index, glyph ->
                glyph.yPos = ((glyphsLastIndex - index) * -TextSize).toFloat()
            }
        }
    }

    private fun Glyph.draw(
        paint: NativePaint,
        drawScope: DrawScope,
        color: Int,
        fixedAlpha: Int?,
        dynamicAlpha: Int
    ) {
        drawScope.drawIntoCanvas {
            it.nativeCanvas.drawTextBlob(
                blob = textBlob(),
                x = xPos,
                y = yPos,
                paint.apply {
                    this.color = color
                    if (fixedAlpha != null) {
                        this.alpha = fixedAlpha
                    } else {
                        this.alpha = dynamicAlpha
                    }
                }
            )
        }
    }

    private fun Glyph.textBlob(): TextBlob {
        return TextBlobBuilder().appendRun(
            font = GlyphFont,
            glyphs = shortArrayOf(char.code.toShort()),
            x = 0f,
            y = 0f,
            bounds = null
        ).build()!!
    }
}

private fun randomChar(): Char {
    val randomInt = randomInt(0, GlyphMaxCharInt)
    return (if (randomInt == AsperandInt || randomInt == LogoGlyph1 || randomInt == LogoGlyph2) BackwardsTwoInt else randomInt).toChar()
}

private fun MutableList<Stream>.addStreams(width: Int, height: Int) {
    var streamXPos = 0
    while (streamXPos < width) {
        add(
            Stream(
                xPos = streamXPos,
                height = height,
                fixedAlpha = 25,
                maxGlyphs = 20,
                speed = slowSpeed()
            )
        )
        streamXPos += TextSize * 2
    }
    streamXPos = 0
    while (streamXPos < width) {
        add(
            Stream(
                xPos = streamXPos,
                height = height,
                speed = fastSpeed()
            )
        )
        streamXPos += TextSize + StreamSpacingDelta
    }
}

private fun randomInt(from: Int, to: Int): Int {
    return Random.nextInt(to - from + 1) + from
}

private fun paint(): NativePaint {
    return NativePaint().apply {
        isAntiAlias = true
        isDither = false
//        setShadowLayer(
//            GlyphShadowRadius,
//            1.0f,
//            -1.0f,
//            GlyphShadow
//        )
    }
}

private fun fastSpeed() = randomInt(FastSpeedMin, FastSpeedMax)
private fun slowSpeed() = randomInt(SlowSpeedMin, SlowSpeedMax)

private fun Int.map(
    oldMin: Float,
    oldMax: Float,
    newMin: Float,
    newMax: Float
): Float {
    return (((this - oldMin) / (oldMax - oldMin)) * (newMax - newMin) + newMin)
}

/**
 * For testing the font glyphs
 */
@Composable
fun CharTest() {
    fun textBlob(char: Char) = TextBlobBuilder()
        .appendRun(
            font = NativeFont().makeWithSize(size = TextSize.toFloat()),
            text = "int = ${char.code.toShort()}. and char = ",
            x = 0f,
            y = 0f,
            bounds = null
        )
        .appendRun(
            font = GlyphFont,
            glyphs = shortArrayOf(char.code.toShort()),
            x = 300f,
            y = 0f,
            bounds = null
        )
        .build()!!

    fun DrawScope.draw(textBlob: TextBlob) {
        drawIntoCanvas {
            it.nativeCanvas.drawTextBlob(
                blob = textBlob,
                x = 200f,
                y = 200f,
                paint = org.jetbrains.skia.Paint().apply { color = BrightGreen }
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        items((0..GlyphMaxCharInt).map { it.toChar() }) { char ->
            Spacer(Modifier.height(30.dp))
            val textBlob = textBlob(char)
            Canvas(modifier = Modifier
                .wrapContentSize(),
                onDraw = { draw(textBlob) }
            )
        }
        item {
            Spacer(Modifier.height(30.dp))
        }
    }
}
