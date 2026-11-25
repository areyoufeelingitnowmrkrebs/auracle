package io.github.areyoufeelingitnowmrkrebs.auracle

import kotlin.math.ceil
import kotlin.random.Random

/**
 * Pure simulation state for the Auracle raining-code matrix effect.
 * Mirrors the behavior of the KDE Plasma and Windows (Lively) implementations,
 * with per-column decay scaling so slow drops keep longer trails while preserving max trail length.
 */
internal class AuracleMatrixSimulation(
    private val random: Random = Random.Default,
) {
    var columns: Int = 0
        private set
    var rows: Int = 0
        private set
    var totalCells: Int = 0
        private set
    var hue: Float = 0f
        private set

    @PublishedApi
    internal var drops: IntArray = IntArray(0)
    @PublishedApi
    internal var speeds: FloatArray = FloatArray(0)
    @PublishedApi
    internal var columnDecayRates: FloatArray = FloatArray(0)
    @PublishedApi
    internal var accumulators: FloatArray = FloatArray(0)
    @PublishedApi
    internal var alphas: FloatArray = FloatArray(0)
    @PublishedApi
    internal var cellDecayRates: FloatArray = FloatArray(0)
    @PublishedApi
    internal var charIndices: IntArray = IntArray(0)
    @PublishedApi
    internal var targetHues: IntArray = IntArray(0)
    @PublishedApi
    internal var xPixels: FloatArray = FloatArray(0)
    @PublishedApi
    internal var yPixels: FloatArray = FloatArray(0)

    private val randomLut: FloatArray = FloatArray(RANDOM_LUT_SIZE)
    private var randomIdx: Int = 0

    fun resize(widthPx: Int, heightPx: Int, cellSizePx: Float) {
        if (widthPx <= 0 || heightPx <= 0 || cellSizePx <= 0f) {
            columns = 0
            rows = 0
            totalCells = 0
            return
        }

        for (i in 0 until RANDOM_LUT_SIZE) {
            randomLut[i] = random.nextFloat()
        }
        randomIdx = 0

        columns = ceil(widthPx / cellSizePx).toInt()
        rows = ceil(heightPx / cellSizePx).toInt()
        totalCells = columns * rows

        drops = IntArray(columns)
        speeds = FloatArray(columns)
        columnDecayRates = FloatArray(columns)
        accumulators = FloatArray(columns)

        alphas = FloatArray(totalCells)
        cellDecayRates = FloatArray(totalCells)
        charIndices = IntArray(totalCells)
        targetHues = IntArray(totalCells)
        xPixels = FloatArray(totalCells)
        yPixels = FloatArray(totalCells)

        for (i in 0 until totalCells) {
            val x = i % columns
            val y = i / columns
            alphas[i] = 0f
            cellDecayRates[i] = MAX_DECAY_RATE
            charIndices[i] = 0
            targetHues[i] = (x * 45 + y * 45) % HUE_DEGREES
            xPixels[i] = x * cellSizePx
            yPixels[i] = y * cellSizePx
        }

        for (x in 0 until columns) {
            drops[x] = (fastRandom() * -rows).toInt()
            val speed = getSpeed()
            speeds[x] = speed
            columnDecayRates[x] = decayRateForSpeed(speed)
            accumulators[x] = 0f
        }
    }

    inline fun stepAndRender(
        dtMillis: Float,
        drawCell: (charIndex: Int, hue: Int, alphaIndex: Int, xPx: Float, yPx: Float) -> Unit,
    ) {
        if (totalCells == 0) return

        val clampedDt = if (dtMillis > 500f || dtMillis < 0f) 16f else dtMillis
        val timeScale = clampedDt / 100f

        hue = (hue + 3f * timeScale) % HUE_DEGREES
        val globalHueInt = hue.toInt()
        val charLen = CHARACTER_STRINGS.size

        for (x in 0 until columns) {
            accumulators[x] += speeds[x] * timeScale
            while (accumulators[x] >= 1f) {
                drops[x]++
                val yIndex = drops[x]

                if (yIndex in 0 until rows) {
                    val cellIndex = yIndex * columns + x
                    charIndices[cellIndex] = (fastRandom() * charLen).toInt().coerceIn(0, charLen - 1)
                    alphas[cellIndex] = 1f
                    cellDecayRates[cellIndex] = columnDecayRates[x]
                }

                if (yIndex > rows && fastRandom() > 0.99f) {
                    drops[x] = -1
                    val nextSpeed = getSpeed()
                    speeds[x] = nextSpeed
                    columnDecayRates[x] = decayRateForSpeed(nextSpeed)
                }
                accumulators[x] -= 1f
            }
        }

        for (i in 0 until totalCells) {
            val a = alphas[i]
            if (a > 0.01f) {
                var currentHue = targetHues[i] + globalHueInt
                if (currentHue >= HUE_DEGREES) currentHue -= HUE_DEGREES

                var alphaIndex = (a * ALPHA_STEPS).toInt()
                if (alphaIndex > ALPHA_STEPS) alphaIndex = ALPHA_STEPS

                drawCell(charIndices[i], currentHue, alphaIndex, xPixels[i], yPixels[i])

                alphas[i] = a - cellDecayRates[i] * timeScale
            } else if (a > 0f) {
                alphas[i] = 0f
            }
        }
    }

    @PublishedApi
    internal fun fastRandom(): Float {
        randomIdx = (randomIdx + 1) % RANDOM_LUT_SIZE
        return randomLut[randomIdx]
    }

    @PublishedApi
    internal fun getSpeed(): Float {
        if (fastRandom() < 0.10f) return MAX_SPEED
        return fastRandom() * (1.2f - 0.6f) + MIN_SPEED
    }

    @PublishedApi
    internal fun decayRateForSpeed(speed: Float): Float {
        val t = ((speed - MIN_SPEED) / (MAX_SPEED - MIN_SPEED)).coerceIn(0f, 1f)
        return MIN_DECAY_RATE + t * (MAX_DECAY_RATE - MIN_DECAY_RATE)
    }

    companion object {
        const val MIN_SPEED: Float = 0.5f
        const val MAX_SPEED: Float = 2.0f
        const val MIN_DECAY_RATE: Float = 0.025f
        const val MAX_DECAY_RATE: Float = 0.05f
        const val HUE_DEGREES: Int = 360
        const val ALPHA_STEPS: Int = 20
        const val RANDOM_LUT_SIZE: Int = 10_000

        const val ALPHABET: String =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&()=+[]{}/<>?" +
                "ЯИГДЖЗЙЛПФЦЧШЩЪЫЬЭЮ" +
                "ァアィイゥウェエォオカガキギクグケゲコゴサザシジスズセゼソゾタダチヂッツヅテデトド" +
                "ナニヌネノハバパヒビピフブプヘベペホボポマミムメモャヤュユョヨラリルレロヮワヰヱヲンヴヵヶヷヸヹヺーヽヾヿ" +
                "ㇰㇱㇲㇳㇴㇵㇶㇷㇸㇹㇺㇻㇼㇽㇾㇿ"

        val CHARACTER_STRINGS: Array<String> = Array(ALPHABET.length) { index ->
            ALPHABET[index].toString()
        }
    }
}
