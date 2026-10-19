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

import android.content.ContentResolver
import android.os.UserHandle
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.itsaky.settings.CustomSettings

fun secureSettingsIntStateOf(
    resolver: ContentResolver,
    key: String,
    defaultValue: Int = 0,
    userId: Int = UserHandle.myUserId(),
): MutableIntState {
    val state = mutableIntStateOf(
        CustomSettings.Secure.getInt(resolver, key, defaultValue, userId)
    )

    return object : MutableIntState by state {
        override var intValue: Int
            get() = state.intValue
            set(value) {
                CustomSettings.Secure.putInt(resolver, key, value, userId)
                state.intValue = value
            }
    }
}

fun secureSettingsBoolStateOf(
    resolver: ContentResolver,
    key: String,
    defaultValue: Boolean = false,
    userId: Int = UserHandle.myUserId(),
): MutableState<Boolean> {
    val state = mutableStateOf(
        CustomSettings.Secure.getBool(resolver, key, defaultValue, userId)
    )

    return object : MutableState<Boolean> by state {
        override var value: Boolean
            get() = state.value
            set(value) {
                CustomSettings.Secure.putBool(resolver, key, value, userId)
                state.value = value
            }
    }
}
