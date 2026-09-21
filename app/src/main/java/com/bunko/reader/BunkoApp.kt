package com.bunko.reader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bunko.reader.crash.CrashReportStore

class BunkoApp : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() {
        super.onCreate()
        CrashReportStore.install(this)
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        if (activity.javaClass.name.contains("leakcanary", ignoreCase = true)) {
            val rootView = activity.findViewById<View>(android.R.id.content)
            rootView?.let { view ->
                ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
                    val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
                    val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                    v.setPadding(
                        v.paddingLeft,
                        statusBarInsets.top,
                        v.paddingRight,
                        navBarInsets.bottom,
                    )
                    insets
                }
            }
        }
    }

    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
