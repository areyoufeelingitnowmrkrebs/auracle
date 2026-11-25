package io.github.areyoufeelingitnowmrkrebs.auracle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.DisplayMetrics
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Renders [AuracleMatrixSimulation] onto an Android [Canvas] using a precomputed ARGB color cache
 * and a pre-rendered [Bitmap.Config.ALPHA_8] glyph atlas.
 */
internal class AuracleMatrixRenderer(
    displayMetrics: DisplayMetrics,
) {
    private val cellSizePx: Float = BASE_FONT_SIZE_DP * displayMetrics.density
    private val simulation = AuracleMatrixSimulation()

    private val drawPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    private val colorCache: IntArray = buildColorCache()
    private val glyphAtlas: GlyphAtlas = buildGlyphAtlas(cellSizePx)
    private val dstRect = RectF()

    fun resize(widthPx: Int, heightPx: Int) {
        simulation.resize(widthPx, heightPx, cellSizePx)
    }

    fun draw(canvas: Canvas, dtMillis: Float) {
        canvas.drawColor(Color.BLACK)

        val alphaStride = AuracleMatrixSimulation.ALPHA_STEPS + 1
        val srcRects = glyphAtlas.srcRects
        val atlasBitmap = glyphAtlas.bitmap
        val offsetX = glyphAtlas.drawOffsetX
        val offsetY = glyphAtlas.drawOffsetY
        val tileW = glyphAtlas.tileWidth.toFloat()
        val tileH = glyphAtlas.tileHeight.toFloat()

        var lastColor = 0
        var hasLastColor = false

        simulation.stepAndRender(dtMillis) { charIndex, hue, alphaIndex, xPx, yPx ->
            val nextColor = colorCache[hue * alphaStride + alphaIndex]
            if (!hasLastColor || nextColor != lastColor) {
                drawPaint.color = nextColor
                lastColor = nextColor
                hasLastColor = true
            }

            val left = xPx + offsetX
            val top = yPx + offsetY
            dstRect.set(left, top, left + tileW, top + tileH)
            canvas.drawBitmap(atlasBitmap, srcRects[charIndex], dstRect, drawPaint)
        }
    }

    fun release() {
        if (!glyphAtlas.bitmap.isRecycled) {
            glyphAtlas.bitmap.recycle()
        }
    }

    private fun buildGlyphAtlas(textSizePx: Float): GlyphAtlas {
        val textPaint = Paint().apply {
            typeface = Typeface.MONOSPACE
            textSize = textSizePx
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
            color = Color.WHITE
        }

        // Aligns cell y coordinates with top edge (matching Canvas2D textBaseline = "top")
        val baselineOffset = -textPaint.ascent()
        val chars = AuracleMatrixSimulation.CHARACTER_STRINGS
        val bounds = Rect()

        var minLeft = 0
        var minTop = 0
        var maxRight = 1
        var maxBottom = 1

        for (i in chars.indices) {
            val ch = chars[i]
            textPaint.getTextBounds(ch, 0, ch.length, bounds)
            if (!bounds.isEmpty) {
                minLeft = min(minLeft, bounds.left)
                minTop = min(minTop, bounds.top)
                maxRight = max(maxRight, bounds.right)
                maxBottom = max(maxBottom, bounds.bottom)
            }
        }

        val margin = ATLAS_MARGIN_PX
        val tileWidth = (maxRight - minLeft) + margin * 2
        val tileHeight = (maxBottom - minTop) + margin * 2

        val gridCols = ceil(sqrt(chars.size.toDouble())).toInt()
        val gridRows = (chars.size + gridCols - 1) / gridCols

        val atlasBitmap = Bitmap.createBitmap(
            gridCols * tileWidth,
            gridRows * tileHeight,
            Bitmap.Config.ALPHA_8,
        )
        val atlasCanvas = Canvas(atlasBitmap)
        val srcRects = Array(chars.size) { Rect() }

        val drawXInTile = (margin - minLeft).toFloat()
        val drawYInTile = (margin - minTop).toFloat()

        for (i in chars.indices) {
            val col = i % gridCols
            val row = i / gridCols
            val tileLeft = col * tileWidth
            val tileTop = row * tileHeight

            srcRects[i].set(tileLeft, tileTop, tileLeft + tileWidth, tileTop + tileHeight)
            atlasCanvas.drawText(
                chars[i],
                tileLeft + drawXInTile,
                tileTop + drawYInTile,
                textPaint,
            )
        }

        atlasBitmap.prepareToDraw()

        return GlyphAtlas(
            bitmap = atlasBitmap,
            srcRects = srcRects,
            tileWidth = tileWidth,
            tileHeight = tileHeight,
            drawOffsetX = (minLeft - margin).toFloat(),
            drawOffsetY = baselineOffset + (minTop - margin).toFloat(),
        )
    }

    private fun buildColorCache(): IntArray {
        val alphaCount = AuracleMatrixSimulation.ALPHA_STEPS + 1
        val cache = IntArray(AuracleMatrixSimulation.HUE_DEGREES * alphaCount)
        val hsv = floatArrayOf(0f, 1f, 1f)

        // hsla(h, 100%, 50%, a / 20.0) is equivalent to HSV(h, 1.0, 1.0) with alpha a / 20.0
        for (h in 0 until AuracleMatrixSimulation.HUE_DEGREES) {
            hsv[0] = h.toFloat()
            for (a in 0..AuracleMatrixSimulation.ALPHA_STEPS) {
                val alphaByte = ((a.toFloat() / AuracleMatrixSimulation.ALPHA_STEPS) * 255f)
                    .roundToInt()
                    .coerceIn(0, 255)
                cache[h * alphaCount + a] = Color.HSVToColor(alphaByte, hsv)
            }
        }
        return cache
    }

    private class GlyphAtlas(
        val bitmap: Bitmap,
        val srcRects: Array<Rect>,
        val tileWidth: Int,
        val tileHeight: Int,
        val drawOffsetX: Float,
        val drawOffsetY: Float,
    )

    companion object {
        private const val BASE_FONT_SIZE_DP: Float = 10f
        private const val ATLAS_MARGIN_PX: Int = 2
    }
}
