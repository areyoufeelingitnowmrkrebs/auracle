package io.github.areyoufeelingitnowmrkrebs.auracle

import android.app.WallpaperColors
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.DisplayMetrics
import android.view.Choreographer
import android.view.SurfaceHolder

class AuracleWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = AuracleEngine()

    private inner class AuracleEngine : Engine(), Choreographer.FrameCallback {

        private val choreographer: Choreographer = Choreographer.getInstance()
        private var renderer: AuracleMatrixRenderer? = null

        private var isVisible = false
        private var hasSurface = false
        private var isDestroyed = false
        private var isFrameScheduled = false

        private var width = 0
        private var height = 0
        private var density = 0f
        private var lastFrameTimeNanos = 0L

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            // Disable unused launcher swipe & touch Binder IPC for lower idle/scroll overhead.
            setOffsetNotificationsEnabled(false)
            setTouchEventsEnabled(false)

            val metrics = resolveDisplayMetrics()
            density = metrics.density
            renderer = AuracleMatrixRenderer(metrics)
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            val metrics = resolveDisplayMetrics()
            val densityChanged = metrics.density != density
            if (densityChanged) {
                density = metrics.density
                renderer?.release()
                renderer = AuracleMatrixRenderer(metrics)
            }

            val sizeChanged = (this.width != width) || (this.height != height)
            if (sizeChanged || densityChanged) {
                this.width = width
                this.height = height
                renderer?.resize(width, height)
            }

            hasSurface = width > 0 && height > 0
            drawFrame(if (sizeChanged || densityChanged) DEFAULT_DT_MILLIS else 0f)
            updateFrameLoop()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            // Redraw current state synchronously for WindowManager without advancing simulation dt.
            drawFrame(0f)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisible = visible
            updateFrameLoop()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            hasSurface = false
            updateFrameLoop()
        }

        override fun onDestroy() {
            isDestroyed = true
            isVisible = false
            hasSurface = false
            updateFrameLoop()
            renderer?.release()
            renderer = null
            super.onDestroy()
        }

        override fun onComputeColors(): WallpaperColors? {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return WallpaperColors(
                    Color.valueOf(HACKER_GREEN_PRIMARY),
                    Color.valueOf(HACKER_GREEN_SECONDARY),
                    Color.valueOf(HACKER_GREEN_TERTIARY),
                    WallpaperColors.HINT_SUPPORTS_DARK_THEME,
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                return WallpaperColors(
                    Color.valueOf(HACKER_GREEN_PRIMARY),
                    Color.valueOf(HACKER_GREEN_SECONDARY),
                    Color.valueOf(HACKER_GREEN_TERTIARY),
                )
            }
            return super.onComputeColors()
        }

        override fun doFrame(frameTimeNanos: Long) {
            isFrameScheduled = false
            if (!shouldRunFrameLoop()) return

            val dtMillis = if (lastFrameTimeNanos == 0L) {
                DEFAULT_DT_MILLIS
            } else {
                (frameTimeNanos - lastFrameTimeNanos) / 1_000_000f
            }
            lastFrameTimeNanos = frameTimeNanos

            drawFrame(dtMillis)

            if (shouldRunFrameLoop() && !isFrameScheduled) {
                isFrameScheduled = true
                choreographer.postFrameCallback(this)
            }
        }

        private fun shouldRunFrameLoop(): Boolean {
            return !isDestroyed && isVisible && hasSurface && width > 0 && height > 0
        }

        private fun updateFrameLoop() {
            if (shouldRunFrameLoop()) {
                if (!isFrameScheduled) {
                    lastFrameTimeNanos = 0L
                    isFrameScheduled = true
                    choreographer.postFrameCallback(this)
                }
            } else if (isFrameScheduled) {
                isFrameScheduled = false
                choreographer.removeFrameCallback(this)
            }
        }

        private fun resolveDisplayMetrics(): DisplayMetrics {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                displayContext?.resources?.displayMetrics?.let { return it }
            }
            return resources.displayMetrics
        }

        private fun drawFrame(dtMillis: Float) {
            val activeRenderer = renderer ?: return
            if (isDestroyed || !hasSurface || width <= 0 || height <= 0) return

            val holder = surfaceHolder
            val surface = holder.surface
            if (surface == null || !surface.isValid) return

            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    holder.lockHardwareCanvas()
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) {
                    activeRenderer.draw(canvas, dtMillis)
                }
            } catch (_: IllegalStateException) {
                // Surface was asynchronously released during a display or picker transition.
            } catch (_: IllegalArgumentException) {
                // Surface buffer queue disconnected before lock completed.
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: IllegalStateException) {
                        // Surface became invalid while rendering the frame.
                    } catch (_: IllegalArgumentException) {
                        // Surface was already released before unlockCanvasAndPost.
                    }
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_DT_MILLIS = 16f
        const val HACKER_GREEN_PRIMARY = 0xFF00FF41.toInt()
        const val HACKER_GREEN_SECONDARY = 0xFF00C853.toInt()
        const val HACKER_GREEN_TERTIARY = 0xFF69F0AE.toInt()
    }
}
