package com.an.anphonetool;

import android.app.Application;
import android.content.Context;
import android.os.StrictMode;

import com.an.anphonetool.core.DesktopConnection;

public class ANApplication extends Application {
    private static Application sApplication;
    public static Application getApplication() {
        return sApplication;
    }

    public static Context getContext() {
        return getApplication().getApplicationContext();
    }

    public static ANApplication getInstance() {
        return (ANApplication) sApplication;
    }

    public DesktopConnection desktopConnection;

    @Override
    public void onCreate() {
        super.onCreate();
        sApplication = this;

        /// below make it possiable to make network request in main thread
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        desktopConnection = new DesktopConnection();
    }
}
