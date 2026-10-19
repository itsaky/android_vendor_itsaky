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

package com.itsaky.parts.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.android.settingslib.spa.widget.preference.ListPreference
import com.android.settingslib.spa.widget.preference.ListPreferenceModel
import com.android.settingslib.spa.widget.preference.ListPreferenceOption
import com.android.settingslib.spa.widget.preference.SwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.widget.ui.SettingsIcon
import com.itsaky.parts.util.rememberSecureSettingsBoolState
import com.itsaky.parts.util.rememberSecureSettingsIntState

class ListOption(val id: Int, @StringRes val labelRes: Int)

class SecureIntSetting(
    val key: String,
    val defaultValue: Int,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val options: List<ListOption>,
)

class SecureBoolSetting(
    val key: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
)

@Composable
fun SecureIntListPreference(setting: SecureIntSetting, isEnabled: Boolean = true) {
    val selected = rememberSecureSettingsIntState(setting.key, setting.defaultValue)
    val entryTitle = stringResource(setting.titleRes)
    val listOptions = setting.options.map {
        ListPreferenceOption(it.id, stringResource(it.labelRes))
    }

    ListPreference(object : ListPreferenceModel {
        override val title = entryTitle
        override val icon = @Composable { SettingsIcon(setting.icon) }
        override val enabled = { isEnabled }
        override val options = listOptions
        override val selectedId = selected
        override val onIdSelected = { id: Int -> selected.intValue = id }
    })
}

@Composable
fun SecureSwitchPreference(
    setting: SecureBoolSetting,
    summaryText: String,
    isEnabled: Boolean = true,
) {
    val checkedState = rememberSecureSettingsBoolState(setting.key)
    val entryTitle = stringResource(setting.titleRes)

    SwitchPreference(object : SwitchPreferenceModel {
        override val title = entryTitle
        override val summary = { summaryText }
        override val icon = @Composable { SettingsIcon(setting.icon) }
        override val changeable = { isEnabled }
        override val checked = { checkedState.value }
        override val onCheckedChange = { newChecked: Boolean -> checkedState.value = newChecked }
    })
}
