package ru.inncreator.sintez

import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.chaquo.python.android.PyApplication
import timber.log.Timber

class App : PyApplication() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        if (! Python.isStarted()) {
            Python.start(AndroidPlatform(applicationContext))
        }
    }
}