package com.itsaky.parts

import android.app.Application
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory

class PartsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SpaEnvironmentFactory.reset(PartsSpaEnvironment(this))
    }
}
