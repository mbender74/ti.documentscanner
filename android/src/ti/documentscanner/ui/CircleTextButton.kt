package ti.documentscanner.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import ti.documentscanner.R
import kotlin.math.abs

/**
 * This class creates a circular done button by modifying an image button. This is used for the
 * add new document button and retake photo button.
 *
 * @param context image button context
 * @param attrs image button attributes
 * @constructor creates circle button
 */
class CircleTextButton(
    context: Context,
    attrs: AttributeSet
): AppCompatImageButton(context, attrs) {
    /**
     * @property ring the button's outer ring
     */
    private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = Rect()

    var text: String? = "3"

    init {
        // set outer ring style
        circle.color = Color.WHITE
        circle.style = Paint.Style.STROKE
        circle.strokeWidth = resources.getDimension(R.dimen.small_button_ring_thickness)
        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
        textPaint.textAlign = Paint.Align.CENTER
    }

    /**
     * This gets called repeatedly. We use it to draw the button
     *
     * @param canvas the image button canvas
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // draw outer ring
        canvas.drawCircle(
            (width / 2).toFloat(),
            (height / 2).toFloat(),
            (width.toFloat() - circle.strokeWidth) / 2,
            circle
        )
        textPaint.textSize = (height / 1.5).toFloat()
        textPaint.getTextBounds(text, 0, text!!.length, r)
        if (text !=null) {
            canvas.drawText(text!!, (width/2).toFloat(), (height/2 + (abs(r.height()))/2).toFloat() , textPaint)
        }
    }
}