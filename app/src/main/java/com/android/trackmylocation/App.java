package com.android.trackmylocation;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.stetho.Stetho;

import timber.log.Timber;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Stetho.initializeWithDefaults(this);
        Timber.plant(new Timber.DebugTree());

    }


}
