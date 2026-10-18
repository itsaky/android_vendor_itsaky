package com.itsaky.settings

import android.content.ContentResolver
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings

object CustomSettings {

    interface SettingsStore {
        fun getInt(resolver: ContentResolver, name: String, default: Int = 0): Int
        fun getLong(resolver: ContentResolver, name: String, default: Long = 0): Long

        fun getBool(resolver: ContentResolver, name: String, default: Boolean = false): Boolean {
            return getInt(resolver, name, if (default) 1 else 0) == 1
        }

        fun getUriFor(name: String): Uri
    }

    object System : SettingsStore {

        override fun getInt(resolver: ContentResolver, name: String, default: Int): Int {
            return Settings.System.getIntForUser(
                resolver,
                name,
                default,
                UserHandle.USER_CURRENT
            )
        }

        override fun getLong(resolver: ContentResolver, name: String, default: Long): Long {
            return Settings.System.getLongForUser(
                resolver,
                name,
                default,
                UserHandle.USER_CURRENT
            )
        }

        override fun getUriFor(name: String): Uri {
            return Settings.System.getUriFor(name)
        }
    }

    object Secure : SettingsStore {

        const val NETWORK_TRAFFIC_MODE = "custom_network_traffic_mode"
        const val NETWORK_TRAFFIC_POSITION = "custom_network_traffic_position"
        const val NETWORK_TRAFFIC_AUTOHIDE = "custom_network_traffic_autohide"
        const val NETWORK_TRAFFIC_HIDE_ARROWS = "custom_network_traffic_hide_arrows"
        const val NETWORK_TRAFFIC_UNITS = "custom_network_traffic_units"
        const val NETWORK_TRAFFIC_SHOW_UNITS = "custom_network_traffic_show_units"

        override fun getInt(resolver: ContentResolver, name: String, default: Int): Int {
            return Settings.Secure.getIntForUser(
                resolver,
                name,
                default,
                UserHandle.USER_CURRENT
            )
        }

        override fun getLong(resolver: ContentResolver, name: String, default: Long): Long {
            return Settings.Secure.getLongForUser(
                resolver,
                name,
                default,
                UserHandle.USER_CURRENT
            )
        }

        override fun getUriFor(name: String): Uri {
            return Settings.Secure.getUriFor(name)
        }
    }
}
