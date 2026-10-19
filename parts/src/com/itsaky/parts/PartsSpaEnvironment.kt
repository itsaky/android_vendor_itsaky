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

package com.itsaky.parts

import android.content.Context
import com.android.settingslib.spa.framework.common.SettingsPageProviderRepository
import com.android.settingslib.spa.framework.common.SpaEnvironment
import com.android.settingslib.spa.framework.common.createSettingsPage
import com.itsaky.parts.settings.PartsSettingsHomeProvider
import com.itsaky.parts.settings.NetworkTrafficSettingsProvider

class PartsSpaEnvironment(
    context: Context,
) : SpaEnvironment(context) {
    override val pageProviderRepository = lazy {
        SettingsPageProviderRepository(
            allPageProviders = listOf(
                PartsSettingsHomeProvider,
                NetworkTrafficSettingsProvider,
            ),
            rootPages = listOf(PartsSettingsHomeProvider.createSettingsPage())
        )
    }

    override val browseActivityClass = PartsSettingsActivity::class.java
    override val searchProviderAuthorities = "com.itsaky.parts.search.provider"
}