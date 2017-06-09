package com.birdbraintechnologies.birdblocks;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // start main web view
        startActivity(new Intent(this, MainWebView.class));

        // close splash activity
        finish();
    }
}
