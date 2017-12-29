package net.nrask.raskface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.content.ContextCompat
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import android.view.WindowInsets
import android.widget.Toast

import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import android.graphics.RectF



class MyWatchFace : CanvasWatchFaceService() {

    companion object {
        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private const val MSG_UPDATE_TIME = 0

        private val NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        private val INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1)
    }

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: MyWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<MyWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {

        private val calendar: Calendar by lazy { Calendar.getInstance() }

        private lateinit var backgroundPaint: Paint
        private lateinit var clockTextPaint: Paint

        private var isLowBitAmbient: Boolean = false
        private var isAmbient: Boolean = false

        private val updateTimeHandler: Handler = EngineHandler(this)

        private var registeredTimeZoneReceiver = false
        private val timeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                calendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build())

            // Initializes background.
            backgroundPaint = Paint().apply {
                color = ContextCompat.getColor(applicationContext, R.color.background)
            }

            // Initializes Watch Face.
            clockTextPaint = Paint().apply {
                typeface = NORMAL_TYPEFACE
                isAntiAlias = true
                color = ContextCompat.getColor(applicationContext, R.color.digital_text)
                textAlign = Paint.Align.CENTER
            }
        }

        override fun onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            isLowBitAmbient = properties.getBoolean(
                    WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            isAmbient = inAmbientMode
            clockTextPaint.isAntiAlias = isLowBitAmbient && !inAmbientMode


            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP ->
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(applicationContext, R.string.message, Toast.LENGTH_SHORT)
                            .show()
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            // Draw the background.
            if (isAmbient) {
                canvas.drawColor(Color.BLACK)
            } else {
                canvas.drawRect(0f, 0f,
                        bounds.width().toFloat(), bounds.height().toFloat(), backgroundPaint)
            }

            // Draw centered digital clock
            val centerX = canvas.width / 2f
            val centerY = canvas.height / 2f

            val now = System.currentTimeMillis()
            calendar.timeInMillis = now

            val text = String.format("%02d", calendar.get(Calendar.HOUR))
            val clockPosY = centerY - ((clockTextPaint.descent() + clockTextPaint.ascent()) / 2f)
            canvas.drawText(text, centerX, clockPosY, clockTextPaint)

            // Top dial
            val topDialCenterX = centerX
            val topDialCenterY = 15f
            val topDialRad = canvas.width / 3f
            val topDialPath = constructCircularDialMarks(
                    topDialCenterX, topDialCenterY, topDialRad, tickInterval = 2
            )
            canvas.drawPath(topDialPath, clockTextPaint)

            // Bottom dial
            val bottomDialCenterX = centerX
            val bottomDialCenterY = canvas.height.toFloat() - 30
            val bottomDialRad = canvas.width / 2f
            val bottomDialPath = constructCircularDialMarks(
                    bottomDialCenterX, bottomDialCenterY, bottomDialRad
            )

            canvas.drawPath(bottomDialPath, clockTextPaint)
        }

        private fun constructCircularDialMarks(
                centerX: Float,
                centerY: Float,
                radius: Float,
                tickInterval: Int = 5,
                numOfHours: Int = 12
        ): Path {

            // Mark specs
            val markWidth = 3
            val markHeight = 15
            val smallMarkHeight = markHeight/1.6f

            val markLeft = centerX - markWidth/2f
            val markRight = markLeft + markWidth
            val markTop = centerY - radius
            val markBot = markTop + markHeight

            val dialPath = Path()

            val numOfMarks = numOfHours * tickInterval
            for (hourMark: Int in 0 until numOfMarks) {
                val isLargeMark = hourMark % tickInterval == 0
                val markTopUpdated = if (isLargeMark) markTop else markTop + (markHeight - smallMarkHeight)

                val markRect = RectF(markLeft, markTopUpdated, markRight, markBot)
                dialPath.addRect(markRect, Path.Direction.CW)

                // Rotate Path
                val mMatrix = Matrix()
                mMatrix.postRotate(360f/numOfMarks, centerX, centerY)
                dialPath.transform(mMatrix)
            }

            return dialPath
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()

                // Update time zone in case it changed while we weren't visible.
                calendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }

        private fun registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return
            }
            registeredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(timeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return
            }
            registeredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(timeZoneReceiver)
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)

            // Load resources that have alternate values for round watches.
            val resources = this@MyWatchFace.resources
            val isRound = insets.isRound

            val textSize = resources.getDimension(
                    if (isRound)
                        R.dimen.digital_text_size_round
                    else
                        R.dimen.digital_text_size
            )

            clockTextPaint.textSize = textSize
        }

        /**
         * Starts the [.updateTimeHandler] timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private fun updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.updateTimeHandler] timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private fun shouldTimerBeRunning(): Boolean = isVisible && !isInAmbientMode

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}
