package com.birdbraintechnologies.birdblocks;

import android.content.Intent;
import android.net.Uri;
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

        // Intent to start MainWebView
        Intent webViewIntent = new Intent(this, MainWebView.class);

//        Bundle bundle = oldIntent.getExtras();
//        if (bundle != null) {
//            for (String key : bundle.keySet()) {
//                Object value = bundle.get(key);
//                Log.d("ShareIntent", String.format("%s %s (%s)", key,
//                        value.toString(), value.getClass().getName()));
//            }
//        }

//        Log.d("ShareIntent", "Data: " + oldIntent.getData());
//        Log.d("ShareIntent", "DataString: " + oldIntent.getDataString());
//        if (oldIntent.getExtras() != null)
//            Log.d("ShareIntent", "Extra Stream: " + oldIntent.getExtras().get(Intent.EXTRA_STREAM).toString());

        // Pass Action of oldIntent to webViewIntent as "Action"
        webViewIntent.putExtra("Action", oldIntent.getAction());

        // Pass File Uri from oldIntent, if present, to webViewIntent as "Data"
        // Else, pass empty string as "Data".
        if (oldIntent.getData() != null) {
            Uri data = Uri.parse(oldIntent.getDataString());
            webViewIntent.putExtra("Scheme", data.getScheme());
            webViewIntent.putExtra("Data", oldIntent.getData().toString());
        } else if (oldIntent.getExtras() != null && oldIntent.getExtras().get(Intent.EXTRA_STREAM) != null) {
            webViewIntent.putExtra("Scheme", "file");
            webViewIntent.putExtra("Data", oldIntent.getExtras().get(Intent.EXTRA_STREAM).toString());
        } else {
            webViewIntent.putExtra("Scheme", "");
            webViewIntent.putExtra("Data", "");
        }

        // start main webView
        startActivity(webViewIntent);

        // close splash activity
        finish();
    }

}
