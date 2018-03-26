package uk.co.ijhdev.trtlware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.roundToInt


class TrtlFace : CanvasWatchFaceService() {

    lateinit var watchLayout : View
    var specW: Int = 0
    var specH:Int = 0
    private val displaySize = Point()

    companion object {
        private val NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

        private const val INTERACTIVE_UPDATE_RATE_MS = 1000

        private const val MSG_UPDATE_TIME = 0
    }

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: TrtlFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<TrtlFace.Engine> = WeakReference(reference)

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

        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false

        private var mXOffset: Float = 0F
        private var mYOffset: Float = 0F

        private lateinit var mBackgroundPaint: Paint
        private lateinit var mTextPaint: Paint

        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false
        private var mAmbient: Boolean = false

        private val mUpdateTimeHandler: Handler = EngineHandler(this)

        private val mTimeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@TrtlFace)
                    .setAcceptsTapEvents(true)
                    .build())

            mCalendar = Calendar.getInstance()

            val resources = this@TrtlFace.resources
            mYOffset = resources.getDimension(R.dimen.digital_y_offset)

            var inflater:LayoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            watchLayout = inflater.inflate(R.layout.watchface, null)
            var display = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            display.defaultDisplay.getSize(displaySize)

        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                    WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(
                    WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode
            updateTimer()
        }

        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> { }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> { }
                WatchFaceService.TAP_TYPE_TAP ->
                    Toast.makeText(applicationContext, R.string.message, Toast.LENGTH_SHORT)
                            .show()
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {

            setTimeandDate()
            setWatchBattery()


            watchLayout.measure(specW, specH);
            watchLayout.layout(0, 0, watchLayout.measuredWidth, watchLayout.measuredHeight)
            canvas.save()
            canvas.translate(mXOffset,mYOffset - 40)
            watchLayout.draw(canvas)
            canvas.restore()
        }

        fun setTimeandDate() {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now
            val date : TextView = watchLayout.findViewById(R.id.date_number)
            date.text = String.format("%02d/%02d", mCalendar.get(Calendar.DAY_OF_MONTH), mCalendar.get(Calendar.MONTH) + 1)
            val hour : TextView = watchLayout.findViewById(R.id.hourtime)
            hour.text = String.format("%02d", mCalendar.get(Calendar.HOUR_OF_DAY))
            val min : TextView = watchLayout.findViewById(R.id.mintime)
            min.text = String.format("%02d", mCalendar.get(Calendar.MINUTE))
        }

        fun setWatchBattery() {
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            val watch : TextView = watchLayout.findViewById(R.id.watch_power)
            watch.text = batLevel.toString() + " %"
        }

        fun setPhoneBattery() {

            val phone : TextView = watchLayout.findViewById(R.id.phone_power)
            phone.text =  " %"
        }

        fun setWeather() {
            val temp : TextView = watchLayout.findViewById(R.id.temp_number)
            temp.text =  " %"
            val weather : ImageView = watchLayout.findViewById(R.id.weather_ico)
        }

        fun setTrtlPrice() {
            val price : TextView = watchLayout.findViewById(R.id.price_ticker)
            price.text =  "The current value of trtl is "
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@TrtlFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@TrtlFace.unregisterReceiver(mTimeZoneReceiver)
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)

            val resources = this@TrtlFace.resources
            val isRound = insets.isRound
            if (isRound) {
                // Shrink the face to fit on a round screen
                mYOffset = displaySize.x * 0.1f
                displaySize.y -= 2 * mXOffset.roundToInt()
                displaySize.x -= 2 * mXOffset.roundToInt()
            } else {
                mXOffset = 0f
            }
            specW = View.MeasureSpec.makeMeasureSpec(displaySize.x, View.MeasureSpec.EXACTLY)
            specH = View.MeasureSpec.makeMeasureSpec(displaySize.y, View.MeasureSpec.EXACTLY)
        }

        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !isInAmbientMode
        }

        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}