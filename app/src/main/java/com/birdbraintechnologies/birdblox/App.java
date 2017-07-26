package com.birdbraintechnologies.birdblox;

import android.app.Application;
import android.content.Context;

/**
 * @author Shreyan Bakshi
 */

public class App extends Application {
    public static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
    }
}
