package fr.coppernic.samples.pcsc;

import android.app.Application;

import timber.log.Timber;

/**
 * Created by michael on 26/01/18.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Timber.plant(new Timber.DebugTree());
    }
}
