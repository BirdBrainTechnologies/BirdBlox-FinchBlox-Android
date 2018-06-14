package com.birdbraintechnologies.birdblox;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class SplashScreen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Intent that starts SplashScreen
        Intent oldIntent = getIntent();

        // Create a new intent to start MainWebView, containing all of
        // the same data as the received intent.
        Intent webViewIntent = new Intent(oldIntent);

        // Set the component to indicate to the webViewIntent where to go
        webViewIntent.setComponent(new ComponentName(this, MainWebView.class));

        // start main webView
        startActivity(webViewIntent);

        // close splash activity
        finish();
    }

}
