/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.itsaky.features.networktraffic

import android.content.Context
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.net.TrafficStats
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.os.UserHandle
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.TextView
import com.itsaky.R
import com.itsaky.settings.CustomSettings

class NetworkTrafficView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : TextView(context, attrs, defStyle) {
    private val mTextSizeSingle: Int
    private val mTextSizeMulti: Int
    private val mTrafficHandler: Handler
    private val mObserver: SettingsObserver

    private var mMode = MODE_DEFAULT
    private var mPosition = POSITION_DEFAULT
    private var mViewPosition = POSITION_DEFAULT
    private var mNetworkTrafficIsVisible: Boolean
    private var mTxKbps: Long = 0
    private var mRxKbps: Long = 0
    private var mLastTxBytes: Long = 0
    private var mLastRxBytes: Long = 0
    private var mLastUpdateTime: Long = 0
    private var mAutoHide = false
    private var mHideArrows = false
    private var mAutoHideThreshold: Long = 0
    private var mUnits = 0
    private var mShowUnits = 0
    private var mIconTint = Color.WHITE
    private var mDrawable: Drawable? = null

    // Used to indicate that the set of sources contributing
    // to current stats have changed.
    private var mNetworksChanged = true

    fun setViewPosition(vpos: Int) {
        mViewPosition = vpos
    }

    init {
        val resources = getResources()
        mTextSizeSingle = resources.getDimensionPixelSize(R.dimen.net_traffic_single_text_size)
        mTextSizeMulti = resources.getDimensionPixelSize(R.dimen.net_traffic_multi_text_size)

        mNetworkTrafficIsVisible = false

        mTrafficHandler = object : Handler(mContext.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    MESSAGE_TYPE_PERIODIC_REFRESH -> {
                        recalculateStats()
                        displayStatsAndReschedule()
                    }

                    MESSAGE_TYPE_UPDATE_VIEW -> displayStatsAndReschedule()
                }
            }

            private fun recalculateStats() {
                val now = SystemClock.elapsedRealtime()
                val timeDelta = now - mLastUpdateTime /* ms */
                if (timeDelta < REFRESH_INTERVAL * 0.95f) {
                    return
                }

                var txBytes = TrafficStats.getTotalTxBytes()
                var rxBytes = TrafficStats.getTotalRxBytes()

                val txBytesDelta = txBytes - mLastTxBytes
                val rxBytesDelta = rxBytes - mLastRxBytes

                if (!mNetworksChanged && timeDelta > 0 && txBytesDelta >= 0 && rxBytesDelta >= 0) {
                    mTxKbps = (txBytesDelta * 8f / 1000f / (timeDelta / 1000f)).toLong()
                    mRxKbps = (rxBytesDelta * 8f / 1000f / (timeDelta / 1000f)).toLong()
                } else if (mNetworksChanged) {
                    mTxKbps = 0
                    mRxKbps = 0
                    mNetworksChanged = false
                }

                mLastTxBytes = txBytes
                mLastRxBytes = rxBytes
                mLastUpdateTime = now
            }

            private fun displayStatsAndReschedule() {
                val enabled =
                    mMode != MODE_DISABLED && mPosition == mViewPosition
                val showUpstream =
                    mMode == MODE_UPSTREAM_ONLY || mMode == MODE_UPSTREAM_AND_DOWNSTREAM
                val showDownstream =
                    mMode == MODE_DOWNSTREAM_ONLY || mMode == MODE_UPSTREAM_AND_DOWNSTREAM
                val shouldHide = mAutoHide
                        && (!showUpstream || mTxKbps < mAutoHideThreshold)
                        && (!showDownstream || mRxKbps < mAutoHideThreshold)

                if (!enabled || shouldHide) {
                    text = ""
                    visibility = GONE
                } else {
                    // Get information for uplink ready so the line return can be added
                    val output = StringBuilder()
                    if (showUpstream) {
                        output.append(formatOutput(mTxKbps))
                    }

                    // Ensure text size is where it needs to be
                    val textSize: Int
                    if (showUpstream && showDownstream) {
                        output.append("\n")
                        textSize = mTextSizeMulti
                    } else {
                        textSize = mTextSizeSingle
                    }

                    // Add information for downlink if it's called for
                    if (showDownstream) {
                        output.append(formatOutput(mRxKbps))
                    }

                    // Update view if there's anything new to show
                    if (!output.toString().contentEquals(getText())) {
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize.toFloat())
                        text = output.toString()
                    }

                    visibility = VISIBLE
                }

                // Schedule periodic refresh
                mTrafficHandler.removeMessages(MESSAGE_TYPE_PERIODIC_REFRESH)
                if (enabled && mNetworkTrafficIsVisible) {
                    mTrafficHandler.sendEmptyMessageDelayed(
                        MESSAGE_TYPE_PERIODIC_REFRESH,
                        REFRESH_INTERVAL.toLong()
                    )
                }
            }

            private fun formatOutput(kbps: Long): String {
                val value: String
                var unit: String? = null
                when (mUnits) {
                    UNITS_KILOBITS -> {
                        value = String.format("%d", kbps)
                        unit = "kb/s"
                    }

                    UNITS_MEGABITS -> {
                        value = String.format("%.1f", kbps.toFloat() / 1000)
                        unit = "mb/s"
                    }

                    UNITS_KILOBYTES, UNITS_AUTO_BYTES -> {
                        if (kbps < 8000 || mUnits == UNITS_KILOBYTES) {
                            value = String.format("%.0f", kbps.toFloat() / 8)
                            unit = if (mShowUnits == SHOW_UNITS_COMPACT)
                                "k"
                            else
                                "kB/s"
                        } else
                            run {
                                val format = if (kbps < 80000) {
                                    "%.2f"
                                } else if (kbps < 800000) {
                                    "%.1f"
                                } else {
                                    "%.0f"
                                }
                                value = String.format(format, kbps.toFloat() / 8000)
                                unit = if (mShowUnits == SHOW_UNITS_COMPACT)
                                    "m"
                                else
                                    "mB/s"
                            }
                    }

                    UNITS_MEGABYTES -> {
                        run {
                            val format = if (kbps < 80000) {
                                "%.2f"
                            } else if (kbps < 800000) {
                                "%.1f"
                            } else {
                                "%.0f"
                            }
                            value = String.format(format, kbps.toFloat() / 8000)
                        }

                        unit = if (mShowUnits == SHOW_UNITS_COMPACT)
                            "m"
                        else
                            "mB/s"
                    }

                    else -> value = "unknown"
                }

                return if (mShowUnits > SHOW_UNITS_OFF && unit != null) {
                    "$value $unit"
                } else {
                    value
                }
            }
        }

        mObserver = SettingsObserver(mTrafficHandler)
        updateSettings()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        mNetworkTrafficIsVisible = true
        mObserver.observe()
        updateSettings()
    }

    protected override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        mNetworkTrafficIsVisible = false
        mObserver.unobserve()
        mTrafficHandler.removeCallbacksAndMessages(null)
    }

    internal inner class SettingsObserver(handler: Handler?) : ContentObserver(handler) {
        fun observe() {
            val resolver = mContext.getContentResolver()
            resolver.registerContentObserver(
                CustomSettings.Secure.getUriFor(
                    CustomSettings.Secure.NETWORK_TRAFFIC_MODE
                ),
                false, this, UserHandle.USER_ALL
            )
            resolver.registerContentObserver(
                CustomSettings.Secure.getUriFor(
                    CustomSettings.Secure.NETWORK_TRAFFIC_POSITION
                ),
                false, this, UserHandle.USER_ALL
            )
            resolver.registerContentObserver(
                CustomSettings.Secure.getUriFor(
                    CustomSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE
                ),
                false, this, UserHandle.USER_ALL
            )
            resolver.registerContentObserver(
                CustomSettings.Secure.getUriFor(
                    CustomSettings.Secure.NETWORK_TRAFFIC_HIDE_ARROWS
                ),
                false, this, UserHandle.USER_ALL
            )
            resolver.registerContentObserver(
                CustomSettings.Secure.getUriFor(
                    CustomSettings.Secure.NETWORK_TRAFFIC_UNITS
                ),
                false, this, UserHandle.USER_ALL
            )
            resolver.registerContentObserver(
                CustomSettings.Secure.getUriFor(
                    CustomSettings.Secure.NETWORK_TRAFFIC_SHOW_UNITS
                ),
                false, this, UserHandle.USER_ALL
            )
        }

        fun unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this)
        }

        override fun onChange(selfChange: Boolean) {
            updateSettings()
        }
    }

    private fun updateSettings() {
        val resolver = mContext.getContentResolver()

        mMode = CustomSettings.Secure.getInt(
            resolver,
            CustomSettings.Secure.NETWORK_TRAFFIC_MODE,
            MODE_DEFAULT,
            UserHandle.USER_CURRENT,
        )
        mPosition = CustomSettings.Secure.getInt(
            resolver,
            CustomSettings.Secure.NETWORK_TRAFFIC_POSITION,
            POSITION_DEFAULT,
            UserHandle.USER_CURRENT,
        )
        mAutoHide = CustomSettings.Secure.getBool(
            resolver,
            CustomSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE,
            false,
            UserHandle.USER_CURRENT,
        )
        mHideArrows = CustomSettings.Secure.getBool(
            resolver,
            CustomSettings.Secure.NETWORK_TRAFFIC_HIDE_ARROWS,
            false,
            UserHandle.USER_CURRENT,
        )
        mUnits = CustomSettings.Secure.getInt(
            resolver,
            CustomSettings.Secure.NETWORK_TRAFFIC_UNITS,
            UNITS_DEFAULT,
            UserHandle.USER_CURRENT,
        )
        mShowUnits = CustomSettings.Secure.getInt(
            resolver,
            CustomSettings.Secure.NETWORK_TRAFFIC_SHOW_UNITS,
            SHOW_UNITS_ON,
            UserHandle.USER_CURRENT,
        )

        mAutoHideThreshold = when (mUnits) {
            UNITS_KILOBITS -> AUTOHIDE_THRESHOLD_KILOBITS
            UNITS_MEGABITS -> AUTOHIDE_THRESHOLD_MEGABITS
            UNITS_KILOBYTES, UNITS_AUTO_BYTES -> AUTOHIDE_THRESHOLD_KILOBYTES
            UNITS_MEGABYTES -> AUTOHIDE_THRESHOLD_MEGABYTES
            else -> 0
        }

        if (mMode != MODE_DISABLED) {
            updateTrafficDrawable()
        }

        updateViewState()
    }

    private fun updateTrafficDrawable() {
        val drawableResId = if (mHideArrows) {
            0
        } else if (mMode == MODE_UPSTREAM_AND_DOWNSTREAM) {
            R.drawable.stat_sys_network_traffic_updown
        } else if (mMode == MODE_UPSTREAM_ONLY) {
            R.drawable.stat_sys_network_traffic_up
        } else if (mMode == MODE_DOWNSTREAM_ONLY) {
            R.drawable.stat_sys_network_traffic_down
        } else {
            0
        }

        @Suppress("DEPRECATION")
        mDrawable = if (drawableResId != 0) resources.getDrawable(drawableResId) else null

        setCompoundDrawablePadding(0)
        setCompoundDrawablesWithIntrinsicBounds(null, null, mDrawable, null)
        mDrawable?.setColorFilter(PorterDuffColorFilter(mIconTint, PorterDuff.Mode.MULTIPLY))
    }

    private fun updateViewState() {
        mTrafficHandler.sendEmptyMessage(MESSAGE_TYPE_UPDATE_VIEW)
    }

    companion object {
        private const val TAG = "NetworkTrafficView"
        private const val MODE_DISABLED = 0
        private const val MODE_UPSTREAM_ONLY = 1
        private const val MODE_DOWNSTREAM_ONLY = 2
        private const val MODE_UPSTREAM_AND_DOWNSTREAM = 3
        const val MODE_DEFAULT = MODE_UPSTREAM_AND_DOWNSTREAM

        private const val POSITION_START = 0
        private const val POSITION_CENTER = 1
        private const val POSITION_END = 2
        private const val POSITION_DEFAULT = POSITION_END

        private const val MESSAGE_TYPE_PERIODIC_REFRESH = 0
        private const val MESSAGE_TYPE_UPDATE_VIEW = 1

        private const val REFRESH_INTERVAL = 2000

        private const val UNITS_KILOBITS = 0
        private const val UNITS_MEGABITS = 1
        private const val UNITS_KILOBYTES = 2
        private const val UNITS_MEGABYTES = 3
        private const val UNITS_AUTO_BYTES = 4
        private const val UNITS_DEFAULT = UNITS_AUTO_BYTES

        private const val SHOW_UNITS_OFF = 0
        private const val SHOW_UNITS_ON = 1
        private const val SHOW_UNITS_COMPACT = 2

        // Thresholds themselves are always defined in kbps
        private const val AUTOHIDE_THRESHOLD_KILOBITS: Long = 10
        private const val AUTOHIDE_THRESHOLD_MEGABITS: Long = 100
        private const val AUTOHIDE_THRESHOLD_KILOBYTES: Long = 8
        private const val AUTOHIDE_THRESHOLD_MEGABYTES: Long = 80
    }
}