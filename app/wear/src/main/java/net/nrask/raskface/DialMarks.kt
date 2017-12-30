package net.nrask.raskface

import android.graphics.*

/**
 * Created by Sebastian Rask Jepsen on 29/12/2017.
 */

class DialMarks(
        val centerX: Float,
        val centerY: Float,
        val radius: Float,
        val tickInterval: Int = 5,
        val numOfHours: Int = 12,
        val markWidth: Float = 3f,
        val markHeight: Float = 15f,
        val minuteMarkHeight: Float = markHeight / 1.6f
): Path() {

    val numOfMarks: Int

    init {
        numOfMarks = numOfHours * tickInterval

        constructMarks()
    }

    fun constructMarks() {
        val markLeft = centerX - markWidth/2f
        val markRight = markLeft + markWidth
        val markTop = centerY - radius
        val markBot = markTop + markHeight

        for (hourMark: Int in 0 until numOfMarks) {
            val isLargeMark = hourMark % tickInterval == 0
            val markTopUpdated = if (isLargeMark) markTop else markTop + (markHeight - minuteMarkHeight)

            val markRect = RectF(markLeft, markTopUpdated, markRight, markBot)
            this.addRect(markRect, Path.Direction.CW)

            // Rotate Path
            val mMatrix = Matrix()
            mMatrix.postRotate(360f/numOfMarks, centerX, centerY)
            this.transform(mMatrix)
        }
    }

    fun getBounds(): RectF {
        val bounds = RectF()
        computeBounds(bounds, true)
        return bounds
    }

    fun drawNumbers(
            start: Int = 0,
            stepDif: Int = 1,
            stepSkip: Int = 1,
            drawUpsideDown: Boolean,
            paint: Paint,
            canvas: Canvas,
            drawBelowMarks: Boolean = false,
            formatNumber: (numberStep: Int) -> String
    ) {
        canvas.save()
        val end: Int = start + numOfMarks / stepSkip
        for (numberStep: Int in start..end) {
            val text = formatNumber(numberStep * stepDif)
            val textBounds = Rect()
            paint.getTextBounds(text, 0, text.length, textBounds)

            val textHeight = textBounds.height() - paint.descent()
            val yDiff = (if (drawBelowMarks) textHeight else -textHeight)
            val posY = centerY - radius + yDiff - (if (drawUpsideDown) paint.descent() else -paint.descent())

            canvas.save()
            canvas.rotate(if (drawUpsideDown) 180f else 0f, centerX, posY)
            canvas.drawText(text, centerX, posY, paint)
            canvas.restore()

            canvas.rotate(360f/(end - start), centerX, centerY)
        }
        canvas.restore()
    }
}