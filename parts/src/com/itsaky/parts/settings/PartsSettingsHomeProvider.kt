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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import com.android.settingslib.spa.widget.scaffold.HomeScaffold
import com.android.settingslib.spa.widget.ui.Category
import com.itsaky.parts.R

object PartsSettingsHomeProvider : SettingsPageProvider {
    override val name = "home"

    override fun getTitle(arguments: Bundle?) =
        SpaEnvironmentFactory.instance.appContext.getString(R.string.parts_settings_label)

    @Composable
    override fun Page(arguments: Bundle?) {
       val title = remember { getTitle(arguments) }
        HomeScaffold(title) {
            Category(title = stringResource(R.string.settings_sb_title)) {
                NtiModeSettingsProvider.Entry()
            }
        }
    }
}