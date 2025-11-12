package de.digbata.pelopen

import android.app.Application
import com.spop.peloton.sensors.util.IsRunningOnPeloton
import timber.log.Timber
import timber.log.Timber.DebugTree

class PelopenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG || IsRunningOnPeloton) {
            Timber.plant(DebugTree())
        }
    }
}

