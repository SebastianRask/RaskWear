package net.nrask.raskface

import android.animation.TimeInterpolator
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
import android.util.Log
import android.view.SurfaceHolder
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast

import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit


class MyWatchFace : CanvasWatchFaceService() {

    companion object {
        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private const val MSG_UPDATE_TIME = 0
        private val INTERACTIVE_UPDATE_RATE_MS = TimeUnit.MILLISECONDS.toMillis(1)

        private const val AMBIENT_CHANGE_ANIMATION_DUR_MS = 1000
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

        private val LOG_TAG: String = "WatchFace"
        private val calendar: Calendar by lazy { Calendar.getInstance() }

        private lateinit var hourTextPaint: Paint
        private lateinit var minuteTextPaint: Paint


        private val updateTimeHandler: Handler = EngineHandler(this)

        private var isLowBitAmbient: Boolean = false
        private var registeredTimeZoneReceiver = false
        private val timeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                calendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        private var ambientChangeTimeStamp: Long = 0
        private val ambientChangeInterpolator: TimeInterpolator = AccelerateDecelerateInterpolator()

        private lateinit var minuteDialPath: DialMarks
        private lateinit var hourDialPath: DialMarks

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build())

            // Initializes typefaces.
            hourTextPaint = Paint().apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
                color = ContextCompat.getColor(applicationContext, R.color.digital_text)
                textAlign = Paint.Align.CENTER
                textSize = baseContext.resources.getDimension( R.dimen.digital_text_size)
            }

            minuteTextPaint = Paint().apply {
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
                color = ContextCompat.getColor(applicationContext, R.color.digital_text)
                textAlign = Paint.Align.CENTER
                textSize = baseContext.resources.getDimension( R.dimen.digital_text_size)
            }

        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            if (holder == null) return

            // Initial Dials
            Log.d(LOG_TAG, String.format("Size - Height %d, width %d", holder.surfaceFrame.height(), holder.surfaceFrame.width()))
            val centerX = holder.surfaceFrame.width() / 2f

            hourDialPath = DialMarks(
                    centerX = centerX,
                    centerY = 0f,
                    radius  = holder.surfaceFrame.width() / 3f,
                    tickInterval = 2
            )

            minuteDialPath = DialMarks(
                    tickInterval = 5,
                    minuteMarkHeight = 5f,
                    centerX = centerX,
                    centerY = holder.surfaceFrame.height().toFloat(),
                    radius  = holder.surfaceFrame.width() / 2f - 70f
            )
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

            ambientChangeTimeStamp = System.currentTimeMillis()
            //hourTextPaint.isAntiAlias = isLowBitAmbient && !inAmbientMode
            //minuteTextPaint.isAntiAlias = isLowBitAmbient && !inAmbientMode

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
            val now = System.currentTimeMillis()
            val animationFactor = calcAnimationFactor(now)
            calendar.timeInMillis = now


            // Draw the background.
            canvas.drawColor(Color.BLACK)

            //Animate scale
            val scale = 1.5f + -0.5f * animationFactor
            canvas.scale(scale, scale, canvas.width / 2f, canvas.height / 2f)

            // Draw hour dial
            val hourRotation = 360f - 360f/(12f/calendar.get(Calendar.HOUR))
            canvas.save()
            canvas.translate(0f, -calcDialTransYPos(animationFactor, hourDialPath, hourTextPaint))
            canvas.rotate(180f + hourRotation, hourDialPath.centerX, hourDialPath.centerY)
            canvas.drawPath(hourDialPath, hourTextPaint)
            hourDialPath.drawNumbers(
                    paint = hourTextPaint,
                    canvas = canvas,
                    drawUpsideDown = true,
                    formatNumber = {
                        if (it == 0)
                            ""
                        else
                            String.format("%02d", it)
                    }
            )
            canvas.restore()

            // Draw minute dial
            val currentMin = calendar.get(Calendar.MINUTE)
            val minuteRotation = 360f - 360f/(60f/currentMin)
            canvas.save()
            canvas.translate(0f, calcDialTransYPos(animationFactor, minuteDialPath))
            canvas.rotate(minuteRotation, minuteDialPath.centerX, minuteDialPath.centerY)
            canvas.drawPath(minuteDialPath, hourTextPaint)
            minuteDialPath.drawNumbers(
                    paint = minuteTextPaint,
                    canvas = canvas,
                    stepDif = 5,
                    drawUpsideDown = false,
                    drawBelowMarks = true,
                    formatNumber = {
                        if (it == 60)
                            ""
                        else
                            String.format("%02d", it)
                    }
            )
            canvas.restore()
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

        private fun calcDialTransYPos(animFactor: Float, dial: DialMarks, ambientTextPaint: Paint? = null): Float {
            val textHeight = if (ambientTextPaint != null)
                ambientTextPaint.getHeightForText("60") + 10
            else
                0f

            val focusPos = 0
            val ambPos = (dial.radius - surfaceHolder.surfaceFrame.height() / 2) + textHeight// - (surfaceHolder.surfaceFrame.height() - dial.centerY)
            return (ambPos - focusPos) * (1 - animFactor)
        }

        private fun calcAnimationFactor(time: Long): Float {
            var timeSinceAmbientChange: Float = Math.min(
                    (time - ambientChangeTimeStamp).toFloat(),
                    AMBIENT_CHANGE_ANIMATION_DUR_MS.toFloat()
            )
            timeSinceAmbientChange -= if (isInAmbientMode) AMBIENT_CHANGE_ANIMATION_DUR_MS else 0

            return ambientChangeInterpolator.getInterpolation(
                    timeSinceAmbientChange / AMBIENT_CHANGE_ANIMATION_DUR_MS
            )
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
        private fun shouldTimerBeRunning(): Boolean = isVisible //&& !isInAmbientMode

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
