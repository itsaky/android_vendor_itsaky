package com.itsaky.settings

import android.content.ContentResolver
import android.os.UserHandle
import android.provider.Settings

object CustomSettings {

    interface SettingsStore {
        fun getInt(resolver: ContentResolver, name: String, default: Int): Int

        fun getBool(resolver: ContentResolver, name: String, default: Boolean): Boolean {
            return getInt(resolver, name, if (default) 1 else 0) == 1
        }
    }

    object System : SettingsStore{

        override fun getInt(resolver: ContentResolver, name: String, default: Int): Int {
            return Settings.System.getIntForUser(
                resolver,
                name,
                default,
                UserHandle.USER_CURRENT
            )
        }
    }

    object Secure : SettingsStore {
        override fun getInt(resolver: ContentResolver, name: String, default: Int): Int {
            return Settings.Secure.getIntForUser(
                resolver,
                name,
                default,
                UserHandle.USER_CURRENT
            )
        }
    }
}

