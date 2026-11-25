package io.github.areyoufeelingitnowmrkrebs.auracle

import android.app.WallpaperColors
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder

class AuracleWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = AuracleEngine()

    private inner class AuracleEngine : Engine(), Choreographer.FrameCallback {

        private val choreographer: Choreographer = Choreographer.getInstance()
        private lateinit var renderer: AuracleMatrixRenderer

        private var isVisible = false
        private var width = 0
        private var height = 0
        private var lastFrameTimeNanos = 0L

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            renderer = AuracleMatrixRenderer(resources.displayMetrics)
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            if (this.width != width || this.height != height) {
                this.width = width
                this.height = height
                renderer.resize(width, height)
            }
            drawFrame(DEFAULT_DT_MILLIS)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisible = visible
            if (visible) {
                lastFrameTimeNanos = 0L
                scheduleNextFrame()
            } else {
                stopFrameLoop()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            isVisible = false
            stopFrameLoop()
        }

        override fun onDestroy() {
            isVisible = false
            stopFrameLoop()
            if (::renderer.isInitialized) {
                renderer.release()
            }
            super.onDestroy()
        }

        override fun onComputeColors(): WallpaperColors? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                return WallpaperColors(
                    Color.valueOf(Color.BLACK),
                    Color.valueOf(Color.CYAN),
                    Color.valueOf(Color.MAGENTA),
                )
            }
            return super.onComputeColors()
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!isVisible) return

            val dtMillis = if (lastFrameTimeNanos == 0L) {
                DEFAULT_DT_MILLIS
            } else {
                (frameTimeNanos - lastFrameTimeNanos) / 1_000_000f
            }
            lastFrameTimeNanos = frameTimeNanos

            drawFrame(dtMillis)
            scheduleNextFrame()
        }

        private fun scheduleNextFrame() {
            choreographer.removeFrameCallback(this)
            choreographer.postFrameCallback(this)
        }

        private fun stopFrameLoop() {
            choreographer.removeFrameCallback(this)
        }

        private fun drawFrame(dtMillis: Float) {
            val holder = surfaceHolder
            if (!holder.surface.isValid || width <= 0 || height <= 0) return

            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) {
                    renderer.draw(canvas, dtMillis)
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_DT_MILLIS = 16f
    }
}
