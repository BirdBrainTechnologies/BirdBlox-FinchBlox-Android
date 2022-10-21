package com.birdbraintechnologies.birdblox.httpservice.RequestHandlers;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.birdbraintechnologies.birdblox.BuildConfig;
import com.birdbraintechnologies.birdblox.Dialogs.DialogType;
import com.birdbraintechnologies.birdblox.MainWebView;
//import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidResponse;
import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidSession;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandler;
import com.birdbraintechnologies.birdblox.httpservice.Status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static android.content.Context.SENSOR_SERVICE;
import static java.lang.Math.abs;

/**
 * Handler for getting sensor data from the host device and showing Dialogs
 *
 * @author Terence Sun (tsun1215)
 */
//public class HostDeviceHandler implements RequestHandler, LocationListener, SensorEventListener {
public class HostDeviceHandler implements RequestHandler, SensorEventListener {

    private final String TAG = this.getClass().getSimpleName();

    /* Constants for location */
    private static final int LOCATION_UPDATE_MILLIS = 100;
    private static final float LOCATION_UPDATE_THRESHOLD = 0.0f;  // in meters

    /* Constants for shake detection */
    private static final int FORCE_THRESHOLD = 350;  // How forceful a shake movement needs to be
    private static final int SAMPLE_THRESHOLD = 100;  // Threshold for detecting shake events
    private static final int SHAKE_TIMEOUT = 500;  // Timeout between movements to count as a shake
    private static final int SHAKE_DURATION = 1000;  // Duration between shake events
    private static final int SHAKE_COUNT = 3;  // Number of successful shakes to count as "shaken"

    /* Variables for shake detection */
    private long lastForcefulMovement, lastShake, lastSampleTime;
    private float lastAccelX = -1.0f, lastAccelY = -1.0f, lastAccelZ = -1.0f;
    private int shakeCount;
    private boolean shaken = false;

    //HttpService service;
    private double longitude = 0, latitude = 0, altitude = 0, pressure = 0,
            deviceAccelX = 0, deviceAccelY = 0, deviceAccelZ = 0;

    /* For Dialogs */
    public static final String DIALOG_RESPONSE = "com.birdbraintechnologies.birdblox.DIALOG_RESPONSE";
    private String dialogResponse = null;
    private BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Handles getting a dialog response from the main activity (see showDialog/showChoice)
            if (intent.getAction().equals(DIALOG_RESPONSE)) {
                dialogResponse = intent.getStringExtra("response");
            }
        }
    };
    LocalBroadcastManager bManager;

    private Context context;

    private LocationManager locationManager;
    private String provider;

    //public HostDeviceHandler(HttpService service) {
    //    this.service = service;
    public HostDeviceHandler(Context context) {
        this.context = context;
        Log.d(TAG, "HostDeviceHandler");
        if (!BuildConfig.IS_FINCHBLOX) {
            Log.d(TAG, "HostDeviceHandler not FINCHBLOX");
            initLocationListener();
        }
        initSensors();
        initBroadcastManager();
    }

    /**
     * Initializes the broadcast manager endpoint for Dialogs
     */
    private void initBroadcastManager() {
        //bManager = LocalBroadcastManager.getInstance(service);
        bManager = LocalBroadcastManager.getInstance(context);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DIALOG_RESPONSE);
        bManager.registerReceiver(bReceiver, intentFilter);
    }

    /**
     * Initializes the sensor manager with sensors that BirdBlocks uses
     */
    private void initSensors() {
        //SensorManager manager = (SensorManager) service.getSystemService(SENSOR_SERVICE);
        SensorManager manager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
        manager.registerListener(this, manager.getDefaultSensor(Sensor.TYPE_PRESSURE),
                SensorManager.SENSOR_DELAY_UI);
        manager.registerListener(this, manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI);
    }

    /**
     * Initializes the location manager to obtain location
     */
    private void initLocationListener() {
        //LocationManager locationManager =
        //        (LocationManager) service.getSystemService(Context.LOCATION_SERVICE);
        locationManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        //if (ActivityCompat.checkSelfPermission(service,
        //        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        if (ActivityCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permissions not granted. Requesting permission now, if possible.");
            Intent getLocPerm = new Intent(MainWebView.LOCATION_PERMISSION);
            //LocalBroadcastManager.getInstance(service).sendBroadcast(getLocPerm);
            LocalBroadcastManager.getInstance(context).sendBroadcast(getLocPerm);
        } else {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setAltitudeRequired(true);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            provider = locationManager.getBestProvider(criteria, true);
            //locationManager.requestLocationUpdates(provider,
              //      LOCATION_UPDATE_MILLIS, LOCATION_UPDATE_THRESHOLD, this);
        }
    }

    @Override
    //public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
    public NativeAndroidResponse handleRequest(NativeAndroidSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        String responseBody = "";
        Map<String, List<String>> m = session.getParameters();
        switch (path[0]) {
            case "shake":
                responseBody = getShaken();
                break;
            case "location":
                return getDeviceLocation();
            case "ssid":
                responseBody = getDeviceSSID();
                break;
            case "pressure":
                return getPressure();
            case "altitude":
                return getDeviceAltitude();
            case "orientation":
                responseBody = getDeviceOrientation();
                break;
            case "acceleration":
                return getDeviceAcceleration();
            case "dialog":
                // showDialog(path[1], path[2], path[3]);
                String title = (m.get("title") == null ? "" : m.get("title").get(0));
                String question = (m.get("question") == null ? "" : m.get("question").get(0));
                String placeholder = (m.get("placeholder") == null ? "" : m.get("placeholder").get(0));
                String prefill = (m.get("prefill") == null ? "" : m.get("prefill").get(0));
                String selectAll = (m.get("selectAll") == null ? "" : m.get("selectAll").get(0));
                String okText = (m.get("okText") == null ? "" : m.get("okText").get(0));
                String cancelText = (m.get("cancelText") == null ? "" : m.get("cancelText").get(0));
                showDialog(title, question, placeholder, prefill, selectAll, okText, cancelText);
                break;
            case "choice":
                String choiceTitle = (m.get("title") == null ? "" : m.get("title").get(0));
                String choiceQ = (m.get("question") == null ? "" : m.get("question").get(0));
                String button1 = (m.get("button1") == null ? "" : m.get("button1").get(0));
                String button2 = (m.get("button2") == null ? "" : m.get("button2").get(0));
                showChoice(choiceTitle, choiceQ, button1, button2);
                break;
            case "dialog_response":
                responseBody = getDialogResponse();
                break;
            case "choice_response":
                responseBody = getChoiceResponse();
                break;
            case "exit":
                exitApp();
                break;
            case "availableSensors":
                return getAvailableSensors();
        }
        //NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
        //        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, responseBody);
        NativeAndroidResponse r = new NativeAndroidResponse(Status.OK, responseBody);
        return r;
    }

    /**
     * Gets the longitude and latitude of the device
     *
     * @return Longitude and latitude separated by a space
     */
    //private NanoHTTPD.Response getDeviceLocation() {
    private NativeAndroidResponse getDeviceLocation() {
        /*
        if (ActivityCompat.checkSelfPermission(service,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Intent showDialog = new Intent(MainWebView.LOCATION_PERMISSION);
            LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Location permission disabled");
        }
        if (ActivityCompat.checkSelfPermission(service,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            final LocationManager locationManager =
                    (LocationManager) service.getSystemService(Context.LOCATION_SERVICE);
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setAltitudeRequired(true);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            final String provider = locationManager.getBestProvider(criteria, true);
            if (provider == null || provider.equals("passive")) {
                new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(mainWebViewContext, "Please enable location services in order to use this block.", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            locationManager.requestLocationUpdates(provider,
                                    LOCATION_UPDATE_MILLIS, LOCATION_UPDATE_THRESHOLD, HostDeviceHandler.this);
                        } catch (SecurityException e) {
                            Log.e("LOCPERMSec", e.getMessage());
                        }
                    }
                });
                return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, Double.toString(latitude) + " " + Double.toString(longitude));
            }
        }
        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Location services disabled");
                */

        if (provider != null && !BuildConfig.IS_FINCHBLOX) { //then location permissions have been granted
            try {
                Location location = locationManager.getLastKnownLocation(provider);
                latitude = location.getLatitude();
                longitude = location.getLongitude();
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException while attempting to get location: " + e.getMessage());
            }
        }

        //return NanoHTTPD.newFixedLengthResponse(
        //        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, Double.toString(latitude) + " " + Double.toString(longitude));
        return new NativeAndroidResponse(Status.OK, Double.toString(latitude) + " " + Double.toString(longitude));
    }


    /**
     * Gets the altitude of the device
     *
     * @return Altitute of device from gps
     */
    //private NanoHTTPD.Response getDeviceAltitude() {
    private NativeAndroidResponse getDeviceAltitude() {
        /*
        PackageManager packageManager = service.getPackageManager();
        boolean gps = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION);
        if (gps) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, Double.toString(altitude));
        } else {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Location services disabled");
        }*/

        if (provider != null && !BuildConfig.IS_FINCHBLOX) { //then location permissions have been granted
            try {
                Location location = locationManager.getLastKnownLocation(provider);
                altitude = location.getAltitude();
            } catch (SecurityException e) {
                Log.e(TAG, "SecurityException while attempting to get location: " + e.getMessage());
            }
        }

        Log.d(TAG, "altitude is " + altitude);
        //return NanoHTTPD.newFixedLengthResponse(
        //        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, Double.toString(altitude));
        return new NativeAndroidResponse(Status.OK, Double.toString(altitude));
    }

    /**
     * Gets the device's wireless SSID, or "null" if there is not one
     *
     * @return Device's SSID or "null" if there is not one
     */
    private String getDeviceSSID() {
        //WifiManager wifiManager = (WifiManager) service.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifiManager.getConnectionInfo();
        String result = info.getSSID();
        result = result.replace("\"", "");
        if (result.trim().equals("<unknown ssid>"))
            result = "null";
        return result;
    }

    /**
     * Gets the atmospheric pressure of the device's environment
     *
     * @return Atmospheric pressure (mPa or mbar, depending on device)
     */
    //private NanoHTTPD.Response getPressure() {
    private NativeAndroidResponse getPressure() {
        /*
        PackageManager packageManager = service.getPackageManager();
        boolean barometer = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER);
        if (barometer) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, Double.toString(pressure));
        } else {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Barometer not detected");
        }*/
        //return NanoHTTPD.newFixedLengthResponse(
        //        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, Double.toString(pressure));
        return new NativeAndroidResponse(Status.OK, Double.toString(pressure));
    }

    /**
     * Gets the device's acceleration
     *
     * @return Each axis' acceleration separated by spaces
     */
    //private NanoHTTPD.Response getDeviceAcceleration() {
    private NativeAndroidResponse getDeviceAcceleration() {
        /*
        PackageManager packageManager = service.getPackageManager();
        boolean accelerometer = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER);
        if (accelerometer) {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, (-deviceAccelX) + " " + (-deviceAccelY) + " " + (-deviceAccelZ));
        } else {
            return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, MIME_PLAINTEXT, "Accelerometer not detected");
        }*/
        //return NanoHTTPD.newFixedLengthResponse(
        //        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, (-deviceAccelX) + " " + (-deviceAccelY) + " " + (-deviceAccelZ));
        return new NativeAndroidResponse(Status.OK, (-deviceAccelX) + " " + (-deviceAccelY) + " " + (-deviceAccelZ));
    }

    /**
     * Gets the shaken status of the device and resets the shaken status
     *
     * @return True if the device was shaken since the last check, False otherwise
     */
    private String getShaken() {
        if (shaken) {
            shaken = false;
            return "1";
        } else {
            return "0";
        }
    }

    /**
     * Shows a text input dialog with a question
     *
     * @param title       Title of the dialog
     * @param question    Text to show to the user
     * @param hint        Placeholder text for the text input
     * @param defaultText Default text present in the input text field initially
     * @param selectAll   If this is 'true' (or any variation of the word 'true'),
     *                    the default text in the input box should be pre-selected.
     */
    private void showDialog(String title, String question, String hint, String defaultText,
                            String selectAll, String okText, String cancelText) {
        dialogResponse = null;
        // Send broadcast to MainWebView
        Intent showDialog = new Intent(MainWebView.SHOW_DIALOG);
        showDialog.putExtra("type", DialogType.INPUT.toString());
        showDialog.putExtra("title", title);
        showDialog.putExtra("message", question);
        showDialog.putExtra("hint", hint);
        showDialog.putExtra("default", defaultText);
        showDialog.putExtra("select", selectAll.toLowerCase().equals("true"));
        showDialog.putExtra("okText", okText);
        showDialog.putExtra("cancelText", cancelText);
        //LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
        LocalBroadcastManager.getInstance(context).sendBroadcast(showDialog);
    }

    /**
     * Shows a 2 button dialog with a question
     *
     * @param title    Title of the dialog
     * @param question Text to show to the user
     * @param option1  Left button text
     * @param option2  Right button text
     */
    private void showChoice(String title, String question, String option1, String option2) {
        dialogResponse = null;
        // Send broadcast to MainWebView
        Intent showDialog = new Intent(MainWebView.SHOW_DIALOG);
        showDialog.putExtra("type", DialogType.CHOICE.toString());
        showDialog.putExtra("title", title);
        showDialog.putExtra("message", question);
        showDialog.putExtra("button1", option1);
        showDialog.putExtra("button2", option2);
        //LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
        LocalBroadcastManager.getInstance(context).sendBroadcast(showDialog);
    }

    /**
     * Gets the response to the text dialog
     *
     * @return Response for the dialog or "No Response" if there was not a response
     */
    private String getDialogResponse() {
        String response;
        if (dialogResponse == null) {
            response = "No Response";
        } else {
            response = dialogResponse;
        }

        dialogResponse = null;
        return response;
    }

    /**
     * Gets the response made to the choice dialog
     *
     * @return Response for the dialog or "0" if there was not a response
     */
    private String getChoiceResponse() {
        String response;
        if (dialogResponse == null) {
            response = "0";
        } else {
            response = dialogResponse;
        }

        dialogResponse = null;
        return response;
    }

    /**
     * Gets the orientation of the device
     *
     * @return String representing the orientation of the device
     */
    private String getDeviceOrientation() {
        //PackageManager packageManager = service.getPackageManager();
        PackageManager packageManager = context.getPackageManager();
        boolean accelerometer = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER);
        String orientation = "Other";
        if (accelerometer) {
            if(abs(-deviceAccelX/9.81 + 1) < 0.1){
                //Landscape: camera on left
                orientation = "landscape_left";
            } else if(abs(-deviceAccelX/9.81 - 1) < 0.15){
                //Landscape: camera on right
                orientation = "landscape_right";
            } else if(abs(-deviceAccelY/9.81 + 1) < 0.15){
                //Portrait: camera on top
                orientation = "portrait_top";
            } else if(abs(-deviceAccelY/9.81 - 1) < 0.15){
                //Portrait: camera on bottom
                orientation = "portrait_bottom";
            } else if(abs(-deviceAccelZ/9.81 + 1) < 0.15){
                orientation = "faceup";
            } else if(abs(-deviceAccelZ/9.81 - 1) < 0.15){
                orientation = "facedown";
            }
            Log.v(TAG, (-deviceAccelX) + " " + (-deviceAccelY) + " " + (-deviceAccelZ));
        }
        return orientation;
    }

    /**
     * Programatically exits the app
     */
    private void exitApp() {
        Intent showDialog = new Intent(MainWebView.EXIT);
        //LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
        LocalBroadcastManager.getInstance(context).sendBroadcast(showDialog);
    }

    /* Location Listener methods
    @Override
    public void onLocationChanged(Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        altitude = location.getAltitude();
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onProviderDisabled(String provider) {

    }
    */

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            pressure = event.values[0];
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Compute whether or not the device was shaken
            long now = System.currentTimeMillis();

            // Timeout count if there was not enough movement
            if ((now - lastForcefulMovement) > SHAKE_TIMEOUT) {
                shakeCount = 0;
            }

            // See if this movement was enough to be a shake
            if ((now - lastSampleTime) > SAMPLE_THRESHOLD) {
                long diff = now - lastSampleTime;
                float speed = abs(event.values[0] + event.values[1] + event.values[2] - lastAccelX - lastAccelY - lastAccelZ) / diff * 10000;
                if (speed > FORCE_THRESHOLD) {
                    if ((++shakeCount >= SHAKE_COUNT) && (now - lastShake > SHAKE_DURATION)) {
                        lastShake = now;
                        shakeCount = 0;
                        shaken = true;
                    }
                    lastForcefulMovement = now;
                }
                lastSampleTime = now;
                lastAccelX = event.values[0];
                lastAccelY = event.values[1];
                lastAccelZ = event.values[2];
            }
            deviceAccelX = event.values[0];
            deviceAccelY = event.values[1];
            deviceAccelZ = event.values[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /**
     * @return
     */
    //private NanoHTTPD.Response getAvailableSensors() {
    private NativeAndroidResponse getAvailableSensors() {
        //SensorManager sensorManager = (SensorManager) service.getSystemService(SENSOR_SERVICE);
        SensorManager sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
        List<String> sensorList = new ArrayList<>();
        //PackageManager packageManager = service.getPackageManager();
        PackageManager packageManager = context.getPackageManager();
        boolean accelerometer = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER);
        if (accelerometer) {
            sensorList.add("accelerometer");
        }
        boolean barometer = packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_BAROMETER);
        if (barometer) {
            sensorList.add("barometer");
        }
        boolean gps = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION);
        if (gps) {
            sensorList.add("gps");
        }
        boolean microphone = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
        if (microphone) {
            sensorList.add("microphone");
        }
        String response = TextUtils.join("\n", sensorList);
        //Log.d("SENSORRESPONSE", response);
        //return NanoHTTPD.newFixedLengthResponse(
        //        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, response);
        return new NativeAndroidResponse(Status.OK, response);
    }
}
