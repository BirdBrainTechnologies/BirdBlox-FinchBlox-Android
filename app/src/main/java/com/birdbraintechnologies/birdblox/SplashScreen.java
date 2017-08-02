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


//        String action = oldIntent.getAction();
//        String scheme = oldIntent.getScheme();
//        String Data = (oldIntent.getData() != null) ? oldIntent.getData().toString() : null;
//        String dataString = oldIntent.getDataString();
//        String extras = (oldIntent.getExtras() != null) ? oldIntent.getExtras().toString() : null;
//        String categories = (oldIntent.getCategories() != null) ? oldIntent.getCategories().toString() : null;
//        String type = oldIntent.getType();
//        String clipData = (oldIntent.getClipData() != null) ? oldIntent.getClipData().toString() : null;
//
//        if (action != null) {
//            Log.d("INTENTTESTSPLASH", "Action: " + action);
////            webViewIntent.setAction(action);
//        }
//        if (scheme != null) {
//            Log.d("INTENTTESTSPLASH", "Scheme: " + scheme);
//        }
//        if (type != null) {
//            Log.d("INTENTTESTSPLASH", "Type: " + type);
////            webViewIntent.setType(type);
//        }
//        if (Data != null) {
//            Log.d("INTENTTESTSPLASH", "Data: " + Data);
////            webViewIntent.setData(oldIntent.getData());
//        }
//        if (dataString != null) {
//            Log.d("INTENTTESTSPLASH", "DataString: " + dataString);
//        }
//        if (extras != null) {
//            Log.d("INTENTTESTSPLASH", "Extras: " + extras);
//        }
//        if (categories != null) {
//            Log.d("INTENTTESTSPLASH", "Categories: " + categories);
//        }


        // Intent to start MainWebView
//        Intent webViewIntent = new Intent(this, MainWebView.class);

//        Bundle bundle = oldIntent.getExtras();
//        if (bundle != null) {
//            for (String key : bundle.keySet()) {
//                Object value = bundle.get(key);
//                Log.d("INTENTTESTSPLASH", "EXTRA: " + String.format("%s %s (%s)", key,
//                        value.toString(), value.getClass().getName()));
//            }
//        }

//        Log.d("ShareIntent", "Data: " + oldIntent.getData());
//        Log.d("ShareIntent", "DataString: " + oldIntent.getDataString());
//        if (oldIntent.getExtras() != null)
//            Log.d("ShareIntent", "Extra Stream: " + oldIntent.getExtras().get(Intent.EXTRA_STREAM).toString());

        // Pass Action of oldIntent to webViewIntent as "Action"
//        webViewIntent.putExtra("Action", oldIntent.getAction());

        // Pass File Uri from oldIntent, if present, to webViewIntent as "Data"
        // Else, pass empty string as "Data".
//        if (oldIntent.getData() != null) {
//            Uri data = Uri.parse(oldIntent.getDataString());
//            webViewIntent.putExtra("Scheme", data.getScheme());
//            webViewIntent.putExtra("Data", oldIntent.getData().toString());
//        } else if (oldIntent.getExtras() != null && oldIntent.getExtras().get(Intent.EXTRA_STREAM) != null) {
//            webViewIntent.putExtra("Scheme", "content");
//            webViewIntent.putExtra("Data", oldIntent.getExtras().get(Intent.EXTRA_STREAM).toString());
//        } else {
//            webViewIntent.putExtra("Scheme", "");
//            webViewIntent.putExtra("Data", "");
//        }


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
