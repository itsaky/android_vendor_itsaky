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
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.android.settingslib.spa.framework.common.EntrySearchData
import com.android.settingslib.spa.framework.common.SettingsEntry
import com.android.settingslib.spa.framework.common.SettingsEntryBuilder
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import com.android.settingslib.spa.framework.common.createSettingsPage
import com.android.settingslib.spa.framework.compose.navigator
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.scaffold.RegularScaffold
import com.android.settingslib.spa.widget.ui.Category
import com.android.settingslib.spa.widget.ui.SettingsIcon
import com.itsaky.features.networktraffic.NetworkTrafficView
import com.itsaky.parts.R
import com.itsaky.parts.util.rememberSecureSettingsIntState
import com.itsaky.settings.CustomSettings
import java.text.DecimalFormat

private class SettingEntry(
    val name: String,
    @StringRes val titleRes: Int,
    val content: @Composable () -> Unit,
)

private class SettingCategory(
    @StringRes val titleRes: Int,
    val entries: List<SettingEntry>,
)

object NetworkTrafficSettingsProvider : SettingsPageProvider {
    override val name = "network_traffic"

    private val owner = createSettingsPage()

    private val mode = SecureIntSetting(
        key = CustomSettings.Secure.NETWORK_TRAFFIC_MODE,
        defaultValue = NetworkTrafficView.MODE_DEFAULT,
        titleRes = R.string.settings_network_traffic_mode_title,
        icon = Icons.Outlined.Tune,
        options = listOf(
            ListOption(
                NetworkTrafficView.MODE_DISABLED,
                R.string.settings_network_traffic_mode_disabled,
            ),
            ListOption(
                NetworkTrafficView.MODE_UPSTREAM_ONLY,
                R.string.settings_network_traffic_mode_upstream_only,
            ),
            ListOption(
                NetworkTrafficView.MODE_DOWNSTREAM_ONLY,
                R.string.settings_network_traffic_mode_downstream_only,
            ),
            ListOption(
                NetworkTrafficView.MODE_UPSTREAM_AND_DOWNSTREAM,
                R.string.settings_network_traffic_mode_upstream_and_downstream,
            ),
        ),
    )

    private val units = SecureIntSetting(
        key = CustomSettings.Secure.NETWORK_TRAFFIC_UNITS,
        defaultValue = NetworkTrafficView.UNITS_DEFAULT,
        titleRes = R.string.settings_network_traffic_units_title,
        icon = Icons.Outlined.Speed,
        options = listOf(
            ListOption(
                NetworkTrafficView.UNITS_KILOBITS,
                R.string.settings_network_traffic_units_kilobits,
            ),
            ListOption(
                NetworkTrafficView.UNITS_MEGABITS,
                R.string.settings_network_traffic_units_megabits,
            ),
            ListOption(
                NetworkTrafficView.UNITS_KILOBYTES,
                R.string.settings_network_traffic_units_kilobytes,
            ),
            ListOption(
                NetworkTrafficView.UNITS_MEGABYTES,
                R.string.settings_network_traffic_units_megabytes,
            ),
            ListOption(
                NetworkTrafficView.UNITS_AUTO_BYTES,
                R.string.settings_network_traffic_units_auto_bytes,
            ),
        ),
    )

    private val showUnits = SecureIntSetting(
        key = CustomSettings.Secure.NETWORK_TRAFFIC_SHOW_UNITS,
        defaultValue = NetworkTrafficView.SHOW_UNITS_ON,
        titleRes = R.string.settings_network_traffic_show_units_title,
        icon = Icons.AutoMirrored.Outlined.Label,
        options = listOf(
            ListOption(
                NetworkTrafficView.SHOW_UNITS_OFF,
                R.string.settings_network_traffic_show_units_off,
            ),
            ListOption(
                NetworkTrafficView.SHOW_UNITS_ON,
                R.string.settings_network_traffic_show_units_on,
            ),
            ListOption(
                NetworkTrafficView.SHOW_UNITS_COMPACT,
                R.string.settings_network_traffic_show_units_compact,
            ),
        ),
    )

    private val autoHide = SecureBoolSetting(
        key = CustomSettings.Secure.NETWORK_TRAFFIC_AUTOHIDE,
        titleRes = R.string.settings_network_traffic_autohide_title,
        icon = Icons.Outlined.VisibilityOff,
    )

    private val hideArrows = SecureBoolSetting(
        key = CustomSettings.Secure.NETWORK_TRAFFIC_HIDE_ARROWS,
        titleRes = R.string.settings_network_traffic_hide_arrows_title,
        icon = Icons.Outlined.SwapVert,
    )

    private val categories = listOf(
        SettingCategory(
            R.string.settings_network_traffic_category_general,
            listOf(
                SettingEntry("mode", mode.titleRes) { SecureIntListPreference(mode) },
                SettingEntry("autohide", autoHide.titleRes) { AutoHidePreference() },
            ),
        ),
        SettingCategory(
            R.string.settings_network_traffic_category_display,
            listOf(
                SettingEntry("units", units.titleRes) {
                    SecureIntListPreference(units, isIndicatorOn())
                },
                SettingEntry("show_units", showUnits.titleRes) {
                    SecureIntListPreference(showUnits, isIndicatorOn())
                },
                SettingEntry("hide_arrows", hideArrows.titleRes) { HideArrowsPreference() },
            ),
        ),
    )

    override fun getTitle(arguments: Bundle?) =
        SpaEnvironmentFactory.instance.appContext.getString(R.string.settings_network_traffic_title)

    override fun buildEntry(arguments: Bundle?): List<SettingsEntry> {
        val context = SpaEnvironmentFactory.instance.appContext
        return categories.flatMap { it.entries }.map { entry ->
            SettingsEntryBuilder.create(entry.name, owner)
                .setSearchDataFn { EntrySearchData(title = context.getString(entry.titleRes)) }
                .setUiLayoutFn { entry.content() }
                .build()
        }
    }

    @Composable
    override fun Page(arguments: Bundle?) {
        val title = remember { getTitle(arguments) }
        RegularScaffold(title) {
            for (category in categories) {
                Category(title = stringResource(category.titleRes)) {
                    category.entries.forEach { it.content() }
                }
            }
        }
    }

    @Composable
    fun Entry() {
        val entryTitle = stringResource(R.string.settings_network_traffic_title)
        val route = remember { owner.buildRoute() }

        Preference(object : PreferenceModel {
            override val title = entryTitle
            override val icon = @Composable { SettingsIcon(Icons.Outlined.NetworkCheck) }
            override val onClick = navigator(route)
        })
    }

    @Composable
    private fun isIndicatorOn(): Boolean {
        val modeState = rememberSecureSettingsIntState(mode.key, mode.defaultValue)
        return modeState.intValue != NetworkTrafficView.MODE_DISABLED
    }

    @Composable
    private fun AutoHidePreference() {
        val selectedUnits = rememberSecureSettingsIntState(units.key, units.defaultValue)
        val threshold = formatAutoHideThreshold(selectedUnits.intValue)

        SecureSwitchPreference(
            setting = autoHide,
            summaryText = stringResource(R.string.settings_network_traffic_autohide_summary, threshold),
            isEnabled = isIndicatorOn(),
        )
    }

    @Composable
    private fun HideArrowsPreference() {
        SecureSwitchPreference(
            setting = hideArrows,
            summaryText = stringResource(R.string.settings_network_traffic_hide_arrows_summary),
            isEnabled = isIndicatorOn(),
        )
    }

    private fun formatAutoHideThreshold(selectedUnits: Int): String {
        val kbps = NetworkTrafficView.autoHideThresholdKbps(selectedUnits)
        return when (selectedUnits) {
            NetworkTrafficView.UNITS_KILOBITS -> "$kbps kb/s"
            NetworkTrafficView.UNITS_MEGABITS -> "${trimmed(kbps / 1000.0)} Mb/s"
            NetworkTrafficView.UNITS_MEGABYTES -> "${trimmed(kbps / 8000.0)} MB/s"
            else -> "${trimmed(kbps / 8.0)} KB/s"
        }
    }

    private fun trimmed(value: Double) = DecimalFormat("0.##").format(value)
}
