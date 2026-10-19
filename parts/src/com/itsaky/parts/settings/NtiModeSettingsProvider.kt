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

import android.os.Bundle
import android.os.UserHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import com.android.settingslib.spa.widget.preference.ListPreference
import com.android.settingslib.spa.widget.preference.ListPreferenceModel
import com.android.settingslib.spa.widget.preference.ListPreferenceOption
import com.android.settingslib.spa.widget.scaffold.RegularScaffold
import com.itsaky.features.networktraffic.NetworkTrafficView
import com.itsaky.parts.R
import com.itsaky.parts.util.secureSettingsIntStateOf
import com.itsaky.settings.CustomSettings

object NtiModeSettingsProvider : SettingsPageProvider {
    private const val NAME = "nti_mode"

    override val name = NAME

    override fun getTitle(arguments: Bundle?) =
        SpaEnvironmentFactory.instance.appContext.getString(R.string.settings_nti_title)

    @Composable
    override fun Page(arguments: Bundle?) {
        val title = remember { getTitle(arguments) }
        RegularScaffold(title) {

        }
    }

    @Composable
    fun Entry() {
        val modeOptions = stringArrayResource(R.array.settings_nti_mode_options)
            .mapIndexed { index, string -> ListPreferenceOption(index, string) }

        val context = LocalContext.current
        val selectedMode =
            remember {
                secureSettingsIntStateOf(
                    context.contentResolver,
                    CustomSettings.Secure.NETWORK_TRAFFIC_MODE,
                    NetworkTrafficView.MODE_DEFAULT,
                    UserHandle.USER_CURRENT,
                )
            }

        ListPreference(object : ListPreferenceModel {
            override val title = getTitle(null)
            override val options = modeOptions
            override val selectedId = selectedMode
            override val onIdSelected = { id: Int ->
                selectedMode.value = id
            }
        })
    }
}