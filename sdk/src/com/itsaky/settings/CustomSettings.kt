package com.itsaky.settings

import android.content.ContentResolver
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings

object CustomSettings {

    interface SettingsStore {
        fun getInt(
            resolver: ContentResolver,
            name: String,
            default: Int = 0,
            userId: Int = UserHandle.myUserId()
        ): Int

        fun putInt(
            resolver: ContentResolver,
            name: String,
            value: Int,
            userId: Int = UserHandle.myUserId(),
        )

        fun getLong(
            resolver: ContentResolver,
            name: String,
            default: Long = 0,
            userId: Int = UserHandle.myUserId(),
        ): Long

        fun putLong(
            resolver: ContentResolver,
            name: String,
            value: Long,
            userId: Int = UserHandle.myUserId(),
        )

        fun getBool(
            resolver: ContentResolver,
            name: String,
            default: Boolean = false,
            userId: Int = UserHandle.myUserId(),
        ): Boolean {
            return getInt(resolver, name, if (default) 1 else 0, userId) == 1
        }

        fun putBool(
            resolver: ContentResolver,
            name: String,
            value: Boolean,
            userId: Int = UserHandle.myUserId(),
        ) {
            putInt(resolver, name, if (value) 1 else 0, userId)
        }

        fun getUriFor(name: String): Uri
    }

    object System : SettingsStore {

        override fun getInt(
            resolver: ContentResolver,
            name: String,
            default: Int,
            userId: Int
        ): Int {
            return Settings.System.getIntForUser(
                resolver,
                name,
                default,
                userId,
            )
        }

        override fun putInt(resolver: ContentResolver, name: String, value: Int, userId: Int) {
            Settings.System.putIntForUser(resolver, name, value, userId)
        }

        override fun getLong(
            resolver: ContentResolver,
            name: String,
            default: Long,
            userId: Int
        ): Long {
            return Settings.System.getLongForUser(
                resolver,
                name,
                default,
                userId,
            )
        }

        override fun putLong(resolver: ContentResolver, name: String, value: Long, userId: Int) {
            Settings.System.putLongForUser(resolver, name, value, userId)
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

        override fun getInt(
            resolver: ContentResolver,
            name: String,
            default: Int,
            userId: Int
        ): Int {
            return Settings.Secure.getIntForUser(
                resolver,
                name,
                default,
                userId,
            )
        }

        override fun putInt(resolver: ContentResolver, name: String, value: Int, userId: Int) {
            Settings.Secure.putIntForUser(resolver, name, value, userId)
        }

        override fun getLong(
            resolver: ContentResolver,
            name: String,
            default: Long,
            userId: Int
        ): Long {
            return Settings.Secure.getLongForUser(
                resolver,
                name,
                default,
                userId,
            )
        }

        override fun putLong(resolver: ContentResolver, name: String, value: Long, userId: Int) {
            Settings.Secure.putLongForUser(resolver, name, value, userId)
        }

        override fun getUriFor(name: String): Uri {
            return Settings.Secure.getUriFor(name)
        }
    }
}
