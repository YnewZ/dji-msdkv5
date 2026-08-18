package dji.sampleV5.aircraft.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class IndoorBatteryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val shellRect = RectF()
    private val fillRect = RectF()
    private var percent = 0

    fun setPercent(value: Int?) {
        percent = value?.coerceIn(0, 100) ?: 0
        fillPaint.color = when {
            percent <= 20 -> Color.rgb(244, 67, 54)
            percent <= 40 -> Color.rgb(255, 193, 7)
            else -> Color.WHITE
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bodyRight = width * 0.86f
        val terminalWidth = max(4f, width * 0.08f)
        val verticalPadding = height * 0.16f
        val corner = height * 0.12f

        shellRect.set(1.5f, verticalPadding, bodyRight, height - verticalPadding)
        canvas.drawRoundRect(shellRect, corner, corner, strokePaint)

        val terminalTop = height * 0.36f
        val terminalBottom = height * 0.64f
        canvas.drawRoundRect(
            RectF(bodyRight + 2f, terminalTop, min(width.toFloat(), bodyRight + terminalWidth), terminalBottom),
            2f,
            2f,
            fillPaint
        )

        val innerPadding = 5f
        val maxFillWidth = max(0f, shellRect.width() - innerPadding * 2)
        val fillWidth = maxFillWidth * percent / 100f
        fillRect.set(
            shellRect.left + innerPadding,
            shellRect.top + innerPadding,
            shellRect.left + innerPadding + fillWidth,
            shellRect.bottom - innerPadding
        )
        canvas.drawRoundRect(fillRect, 3f, 3f, fillPaint)
    }
}
