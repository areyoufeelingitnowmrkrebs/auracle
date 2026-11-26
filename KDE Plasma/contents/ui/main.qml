import QtQuick
import org.kde.plasma.plasmoid

WallpaperItem {
    id: root
    // Drops spawn at y < 0, so frame 0 is solid black; explicit accentColor prevents black-frame sampling.
    accentColor: "#00ff41"
    implicitWidth: 1920
    implicitHeight: 1080

    Canvas {
        id: canvas
        anchors.fill: parent

        renderTarget: Canvas.FramebufferObject
        renderStrategy: Canvas.Threaded
        antialiasing: true
        smooth: true

        readonly property int fontSize: 14
        readonly property real minSpeed: 0.5
        readonly property real maxSpeed: 2.0
        // Tuned between legacy flat 0.05 (~10 min cells) and Android portrait 0.025 (~20 min cells).
        readonly property real minDecayRate: 0.035
        readonly property real maxDecayRate: 0.05
        readonly property int hueDegrees: 360
        readonly property int hueBucketsCount: 8
        readonly property int alphaSteps: 20
        // Below 1/alphaSteps, (a * alphaSteps) | 0 === 0 (fully transparent); cull immediately.
        readonly property real minVisibleAlpha: 0.05
        readonly property int randomLutSize: 16384
        readonly property int randomLutMask: 16383
        readonly property string alphabet: "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&()=+[]{}/<>?ЯИГДЖЗЙЛПФЦЧШЩЪЫЬЭЮァアィイゥウェエォオカガキギクグケゲコゴサザシジスズセゼソゾタダチヂッツヅテデトドナニヌネノハバパヒビピフブプヘベペホボポマミムメモャヤュユョヨラリルレロヮワヰヱヲンヴヵヶヷヸヹヺーヽヾヿㇰㇱㇲㇳㇴㇵㇶㇷㇸㇹㇺㇻㇼㇽㇾㇿ"

        property var characters: []
        property var colorCache: []
        property var randomLUT: null
        property int randomIdx: 0

        property int lastWidth: 0
        property int lastHeight: 0
        property int columns: 0
        property int rows: 0
        property int totalCells: 0
        property int activeCount: 0
        property real hue: 0.0
        property real pendingDtMs: 0.0

        property var drops: null
        property var speeds: null
        property var columnDecayRates: null
        property var accumulators: null

        property var alphas: null
        property var cellDecayRates: null
        property var charIndices: null
        property var hueBuckets: null
        property var xPixels: null
        property var yPixels: null

        property var activeCells: null
        property var isActive: null
        property var bucketHeads: null
        property var nextInBucket: null

        function fastRandom() {
            const nextIdx = (canvas.randomIdx + 1) & canvas.randomLutMask;
            canvas.randomIdx = nextIdx;
            return canvas.randomLUT[nextIdx];
        }

        function getSpeed() {
            if (canvas.fastRandom() < 0.10) {
                return canvas.maxSpeed;
            }
            return canvas.fastRandom() * 0.6 + canvas.minSpeed;
        }

        function decayRateForSpeed(speed) {
            let t = (speed - canvas.minSpeed) / (canvas.maxSpeed - canvas.minSpeed);
            if (t < 0.0) {
                t = 0.0;
            } else if (t > 1.0) {
                t = 1.0;
            }
            return canvas.minDecayRate + t * (canvas.maxDecayRate - canvas.minDecayRate);
        }

        function initStaticResources() {
            canvas.characters = canvas.alphabet.split("");

            // Pre-convert to QColor via Qt.hsla so Context2D.fillStyle takes Qt's C++ QColor fast path.
            const cache = new Array(canvas.hueDegrees * canvas.alphaSteps);
            for (let h = 0; h < canvas.hueDegrees; h++) {
                const hNorm = h / 360.0;
                const base = h * canvas.alphaSteps;
                for (let a = 1; a <= canvas.alphaSteps; a++) {
                    cache[base + (a - 1)] = Qt.hsla(hNorm, 1.0, 0.5, a / 20.0);
                }
            }
            canvas.colorCache = cache;

            // Float32 rounds >= (1 - 2^-25) up to 1.0; clamp to max float32 < 1.0 so (r * len) | 0 never hits len.
            const maxBelowOne = 0.99999994;
            const lut = new Float32Array(canvas.randomLutSize);
            for (let i = 0; i < canvas.randomLutSize; i++) {
                const r = Math.random();
                lut[i] = r < maxBelowOne ? r : maxBelowOne;
            }
            canvas.randomLUT = lut;
            canvas.randomIdx = 0;
            canvas.bucketHeads = new Int32Array(canvas.hueBucketsCount * canvas.alphaSteps);
        }

        function syncSize() {
            if (!canvas.randomLUT) {
                return;
            }

            const w = Math.floor(canvas.width);
            const h = Math.floor(canvas.height);

            if (w <= 0 || h <= 0) {
                canvas.lastWidth = 0;
                canvas.lastHeight = 0;
                canvas.columns = 0;
                canvas.rows = 0;
                canvas.totalCells = 0;
                canvas.activeCount = 0;
                canvas.pendingDtMs = 0.0;
                return;
            }

            if (w === canvas.lastWidth && h === canvas.lastHeight) {
                return;
            }

            canvas.lastWidth = w;
            canvas.lastHeight = h;

            const cellPx = canvas.fontSize;
            const cols = Math.ceil(w / cellPx) | 0;
            const rws = Math.ceil(h / cellPx) | 0;
            const total = cols * rws;

            canvas.columns = cols;
            canvas.rows = rws;
            canvas.totalCells = total;
            canvas.activeCount = 0;

            const dropsArr = new Int32Array(cols);
            const speedsArr = new Float32Array(cols);
            const colDecaysArr = new Float32Array(cols);
            const accumArr = new Float32Array(cols);

            const alphasArr = new Float32Array(total);
            const cellDecaysArr = new Float32Array(total);
            const charIdxArr = new Uint8Array(total);
            // (x * 45 + y * 45) % 360 === ((x + y) & 7) * 45; only 8 distinct diagonal hue offsets exist.
            const hueBucketsArr = new Uint8Array(total);
            const xPxArr = new Float32Array(total);
            const yPxArr = new Float32Array(total);

            const activeArr = new Int32Array(total);
            const isActiveArr = new Uint8Array(total);
            const nextInBucketArr = new Int32Array(total);

            for (let i = 0; i < total; i++) {
                const x = i % cols;
                const y = (i / cols) | 0;
                cellDecaysArr[i] = canvas.maxDecayRate;
                hueBucketsArr[i] = (x + y) & 7;
                xPxArr[i] = x * cellPx;
                yPxArr[i] = y * cellPx;
            }

            for (let x = 0; x < cols; x++) {
                dropsArr[x] = (canvas.fastRandom() * -rws) | 0;
                const spd = canvas.getSpeed();
                speedsArr[x] = spd;
                colDecaysArr[x] = canvas.decayRateForSpeed(spd);
            }

            canvas.drops = dropsArr;
            canvas.speeds = speedsArr;
            canvas.columnDecayRates = colDecaysArr;
            canvas.accumulators = accumArr;
            canvas.alphas = alphasArr;
            canvas.cellDecayRates = cellDecaysArr;
            canvas.charIndices = charIdxArr;
            canvas.hueBuckets = hueBucketsArr;
            canvas.xPixels = xPxArr;
            canvas.yPixels = yPxArr;
            canvas.activeCells = activeArr;
            canvas.isActive = isActiveArr;
            canvas.nextInBucket = nextInBucketArr;

            canvas.pendingDtMs = 16.0;
            if (canvas.available) {
                canvas.requestPaint();
            }
        }

        onWidthChanged: Qt.callLater(canvas.syncSize)
        onHeightChanged: Qt.callLater(canvas.syncSize)
        onAvailableChanged: {
            if (canvas.available && canvas.totalCells > 0) {
                canvas.requestPaint();
            }
        }

        Component.onCompleted: {
            canvas.initStaticResources();
            Qt.callLater(canvas.syncSize);
        }

        onPaint: {
            const ctx = canvas.getContext("2d");
            const total = canvas.totalCells;
            if (!ctx || total === 0) {
                return;
            }

            // pendingDtMs === 0 occurs on spontaneous FBO repaints; redraw current state without advancing.
            let dt = canvas.pendingDtMs;
            canvas.pendingDtMs = 0.0;
            if (!(dt >= 0.0) || !Number.isFinite(dt) || dt > 500.0) {
                dt = 16.0;
            }
            const timeScale = dt / 100.0;

            ctx.fillStyle = "#000000";
            ctx.fillRect(0, 0, canvas.width, canvas.height);
            ctx.font = canvas.fontSize + "px monospace";
            ctx.textBaseline = "top";

            let nextHue = (canvas.hue + 3.0 * timeScale) % 360.0;
            if (nextHue < 0.0) {
                nextHue += 360.0;
            }
            canvas.hue = nextHue;
            const globalHueInt = nextHue | 0;

            const cols = canvas.columns;
            const rws = canvas.rows;
            const chars = canvas.characters;
            const charLen = chars.length;
            const lut = canvas.randomLUT;
            const lutMask = canvas.randomLutMask;
            let rIdx = canvas.randomIdx;

            const dropsArr = canvas.drops;
            const speedsArr = canvas.speeds;
            const colDecaysArr = canvas.columnDecayRates;
            const accumArr = canvas.accumulators;
            const alphasArr = canvas.alphas;
            const cellDecaysArr = canvas.cellDecayRates;
            const charIdxArr = canvas.charIndices;
            const hueBucketsArr = canvas.hueBuckets;
            const xPxArr = canvas.xPixels;
            const yPxArr = canvas.yPixels;
            const activeArr = canvas.activeCells;
            const isActiveArr = canvas.isActive;
            const heads = canvas.bucketHeads;
            const nextInBucketArr = canvas.nextInBucket;

            let activeCnt = canvas.activeCount;

            if (timeScale > 0.0) {
                const minSpd = canvas.minSpeed;
                const maxSpd = canvas.maxSpeed;
                const spdRangeInv = 1.0 / (maxSpd - minSpd);
                const minDec = canvas.minDecayRate;
                const decSpan = canvas.maxDecayRate - minDec;

                for (let x = 0; x < cols; x++) {
                    accumArr[x] += speedsArr[x] * timeScale;
                    while (accumArr[x] >= 1.0) {
                        const yIndex = ++dropsArr[x];

                        if (yIndex >= 0 && yIndex < rws) {
                            const cellIndex = yIndex * cols + x;
                            rIdx = (rIdx + 1) & lutMask;
                            charIdxArr[cellIndex] = (lut[rIdx] * charLen) | 0;
                            alphasArr[cellIndex] = 1.0;
                            // Latch column decay onto cell so respawning the column doesn't alter existing trail fade.
                            cellDecaysArr[cellIndex] = colDecaysArr[x];
                            if (isActiveArr[cellIndex] === 0) {
                                isActiveArr[cellIndex] = 1;
                                activeArr[activeCnt++] = cellIndex;
                            }
                        }

                        if (yIndex > rws) {
                            rIdx = (rIdx + 1) & lutMask;
                            if (lut[rIdx] > 0.99) {
                                dropsArr[x] = -1;
                                rIdx = (rIdx + 1) & lutMask;
                                let nextSpeed;
                                if (lut[rIdx] < 0.10) {
                                    nextSpeed = maxSpd;
                                } else {
                                    rIdx = (rIdx + 1) & lutMask;
                                    nextSpeed = lut[rIdx] * 0.6 + minSpd;
                                }
                                speedsArr[x] = nextSpeed;
                                let t = (nextSpeed - minSpd) * spdRangeInv;
                                if (t < 0.0) {
                                    t = 0.0;
                                } else if (t > 1.0) {
                                    t = 1.0;
                                }
                                colDecaysArr[x] = minDec + t * decSpan;
                            }
                        }

                        accumArr[x] -= 1.0;
                    }
                }
                canvas.randomIdx = rIdx;
            }

            if (activeCnt === 0) {
                canvas.activeCount = 0;
                return;
            }

            heads.fill(-1);
            let anyVisible = false;
            const minAlpha = canvas.minVisibleAlpha;
            let i = 0;

            while (i < activeCnt) {
                const cellIndex = activeArr[i];
                const a = alphasArr[cellIndex];
                let alphaIndex = (a * 20.0) | 0;
                if (alphaIndex > 20) {
                    alphaIndex = 20;
                }

                if (alphaIndex > 0) {
                    const bucket = hueBucketsArr[cellIndex] * 20 + (alphaIndex - 1);
                    nextInBucketArr[cellIndex] = heads[bucket];
                    heads[bucket] = cellIndex;
                    anyVisible = true;

                    const nextAlpha = a - cellDecaysArr[cellIndex] * timeScale;
                    if (nextAlpha >= minAlpha) {
                        alphasArr[cellIndex] = nextAlpha;
                        i++;
                        continue;
                    }
                }

                alphasArr[cellIndex] = 0.0;
                isActiveArr[cellIndex] = 0;
                activeArr[i] = activeArr[--activeCnt];
            }

            canvas.activeCount = activeCnt;

            if (!anyVisible) {
                return;
            }

            // Batching by (hueBucket, alphaSlot) caps ctx.fillStyle changes at <= 160/frame instead of per-cell.
            const colors = canvas.colorCache;
            for (let hBucket = 0; hBucket < 8; hBucket++) {
                let hueDeg = globalHueInt + hBucket * 45;
                if (hueDeg >= 360) {
                    hueDeg -= 360;
                }
                const colorBase = hueDeg * 20;
                const bucketBase = hBucket * 20;

                for (let aSlot = 0; aSlot < 20; aSlot++) {
                    let cellIndex = heads[bucketBase + aSlot];
                    if (cellIndex === -1) {
                        continue;
                    }

                    ctx.fillStyle = colors[colorBase + aSlot];
                    do {
                        ctx.fillText(chars[charIdxArr[cellIndex]], xPxArr[cellIndex], yPxArr[cellIndex]);
                        cellIndex = nextInBucketArr[cellIndex];
                    } while (cellIndex !== -1);
                }
            }
        }

        FrameAnimation {
            id: frameLoop
            running: root.visible
                && canvas.visible
                && canvas.available
                && canvas.totalCells > 0
                && (root.Window.window ? root.Window.window.visible : true)
            onRunningChanged: {
                if (!running) {
                    canvas.pendingDtMs = 0.0;
                }
            }
            onTriggered: {
                canvas.pendingDtMs += frameLoop.frameTime * 1000.0;
                canvas.requestPaint();
            }
        }
    }
}
