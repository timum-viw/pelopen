package de.digbata.pelopen

import android.app.Application
import android.util.Log
import com.spop.peloton.sensors.util.IsRunningOnPeloton
import timber.log.Timber
import timber.log.Timber.DebugTree

class PelopenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Always plant Timber - remove condition to ensure it works
        Timber.plant(DebugTree())
        
        // Log with both Android Log and Timber to verify
        Log.d("PelopenApplication", "Application onCreate - DEBUG=${BuildConfig.DEBUG}, IsRunningOnPeloton=$IsRunningOnPeloton")
        Timber.d("Timber initialized - DEBUG=${BuildConfig.DEBUG}, IsRunningOnPeloton=$IsRunningOnPeloton")
    }
}

