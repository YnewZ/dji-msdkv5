package dji.sampleV5.aircraft.aruco

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class ArucoOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }

    private var detection: ArucoDetection? = null

    fun updateDetection(detection: ArucoDetection?) {
        this.detection = detection
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = detection ?: return
        drawFrameCenter(canvas)
        if (!current.visible || current.frameWidth <= 0 || current.frameHeight <= 0) {
            canvas.drawText("Aruco ID 1: not detected", 24f, 48f, textPaint)
            return
        }

        val scaleX = width.toFloat() / current.frameWidth.toFloat()
        val scaleY = height.toFloat() / current.frameHeight.toFloat()
        val points = current.corners
        if (points.size == 4) {
            for (i in points.indices) {
                val start = points[i]
                val end = points[(i + 1) % points.size]
                canvas.drawLine(
                    start.x * scaleX,
                    start.y * scaleY,
                    end.x * scaleX,
                    end.y * scaleY,
                    markerPaint
                )
            }
        }

        val markerX = current.centerX * scaleX
        val markerY = current.centerY * scaleY
        canvas.drawCircle(markerX, markerY, 16f, markerPaint)
        canvas.drawLine(width / 2f, height / 2f, markerX, markerY, markerPaint)
        canvas.drawText(
            "ID ${current.markerId}  errX=${"%.2f".format(current.normalizedErrorX)}  errY=${"%.2f".format(current.normalizedErrorY)}  rot=${"%.0f".format(current.rotationDegrees)}  conf=${"%.2f".format(current.confidence)}",
            24f,
            48f,
            textPaint
        )
    }

    private fun drawFrameCenter(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawLine(cx - 35f, cy, cx + 35f, cy, centerPaint)
        canvas.drawLine(cx, cy - 35f, cx, cy + 35f, centerPaint)
        canvas.drawCircle(cx, cy, 45f, centerPaint)
    }
}
