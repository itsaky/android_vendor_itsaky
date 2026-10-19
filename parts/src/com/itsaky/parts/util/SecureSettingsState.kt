/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.itsaky.parts.util

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.itsaky.settings.CustomSettings

/**
 * Remembers a secure setting as a state that follows external changes and writes through on set.
 */
@Composable
fun rememberSecureSettingsIntState(
    key: String,
    defaultValue: Int = 0,
    userId: Int = UserHandle.myUserId(),
): MutableIntState {
    val resolver = LocalContext.current.contentResolver
    val read = { CustomSettings.Secure.getInt(resolver, key, defaultValue, userId) }
    val state = remember(key, defaultValue, userId) { mutableIntStateOf(read()) }

    ObserveSecureSetting(key, userId) { state.intValue = read() }

    return remember(state) {
        object : MutableIntState by state {
            override var intValue: Int
                get() = state.intValue
                set(newValue) {
                    CustomSettings.Secure.putInt(resolver, key, newValue, userId)
                    state.intValue = newValue
                }

            override var value: Int
                get() = intValue
                set(newValue) {
                    intValue = newValue
                }

            override fun component2(): (Int) -> Unit = { intValue = it }
        }
    }
}

/**
 * Remembers a boolean secure setting as a state that follows external changes and writes through.
 */
@Composable
fun rememberSecureSettingsBoolState(
    key: String,
    defaultValue: Boolean = false,
    userId: Int = UserHandle.myUserId(),
): MutableState<Boolean> {
    val intState = rememberSecureSettingsIntState(key, if (defaultValue) 1 else 0, userId)

    return remember(intState) {
        object : MutableState<Boolean> {
            override var value: Boolean
                get() = intState.intValue == 1
                set(newValue) {
                    intState.intValue = if (newValue) 1 else 0
                }

            override fun component1() = value

            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}

@Composable
private fun ObserveSecureSetting(key: String, userId: Int, onChange: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val latestOnChange = rememberUpdatedState(onChange)

    DisposableEffect(resolver, key, userId) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = latestOnChange.value()
        }
        resolver.registerContentObserver(
            CustomSettings.Secure.getUriFor(key),
            false,
            observer,
            userId,
        )
        latestOnChange.value()

        onDispose { resolver.unregisterContentObserver(observer) }
    }
}
