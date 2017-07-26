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
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.birdbraintechnologies.birdblox.Dialogs.DialogType;
import com.birdbraintechnologies.birdblox.MainWebView;
import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandler;

import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

/**
 * Handler for getting sensor data from the host device and showing Dialogs
 *
 * @author Terence Sun (tsun1215)
 */
public class HostDeviceHandler implements RequestHandler, LocationListener, SensorEventListener {
    private static final String TAG = HostDeviceHandler.class.getName();

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

    HttpService service;
    private double longitude, latitude, altitude, pressure,
            deviceAccelX, deviceAccelY, deviceAccelZ;


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

    public HostDeviceHandler(HttpService service) {
        this.service = service;
        initLocationListener();
        initSensors();
        initBroadcastManager();
    }

    /**
     * Initializes the broadcast manager endpoint for Dialogs
     */
    private void initBroadcastManager() {
        bManager = LocalBroadcastManager.getInstance(service);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DIALOG_RESPONSE);
        bManager.registerReceiver(bReceiver, intentFilter);

    }

    /**
     * Initializes the sensor manager with sensors that BirdBlocks uses
     */
    private void initSensors() {
        SensorManager manager = (SensorManager) service.getSystemService(Context.SENSOR_SERVICE);
        manager.registerListener(this, manager.getDefaultSensor(Sensor.TYPE_PRESSURE),
                SensorManager.SENSOR_DELAY_UI);
        manager.registerListener(this, manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI);
    }

    /**
     * Initializes the location manager to obtain location
     */
    private void initLocationListener() {
        LocationManager locationManager =
                (LocationManager) service.getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(service,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(service,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Unable to obtain location");
            // TODO: Handler error and API 25+ permissions
            // See: https://developer.android.com/training/permissions/requesting.html
            // Probably should setup another BroadcastReceiver on the MainWebView
        } else {
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setAltitudeRequired(true);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            String provider = locationManager.getBestProvider(criteria, true);
            locationManager.requestLocationUpdates(provider,
                    LOCATION_UPDATE_MILLIS, LOCATION_UPDATE_THRESHOLD, this);
        }
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        String responseBody = "";
        Map<String, List<String>> m = session.getParameters();
        switch (path[0]) {
            case "shake":
                responseBody = getShaken();
                break;
            case "location":
                responseBody = getDeviceLocation();
                if (responseBody.equals("403 Forbidden"))
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.FORBIDDEN, MIME_PLAINTEXT, responseBody);
                break;
            case "ssid":
                responseBody = getDeviceSSID();
                break;
            case "pressure":
                responseBody = getPressure();
                break;
            case "altitude":
                responseBody = getDeviceAltitude();
                break;
            case "orientation":
                responseBody = getDeviceOrientation();
                break;
            case "acceleration":
                responseBody = getDeviceAcceleration();
                break;
            case "dialog":
                // showDialog(path[1], path[2], path[3]);
                String title = (m.get("title") == null ? "" : m.get("title").get(0));
                String question = (m.get("question") == null ? "" : m.get("question").get(0));
                String placeholder = (m.get("placeholder") == null ? "" : m.get("placeholder").get(0));
                String prefill = (m.get("prefill") == null ? "" : m.get("prefill").get(0));
                String selectAll = (m.get("selectAll") == null ? "" : m.get("selectAll").get(0));
                showDialog(title, question, placeholder, prefill, selectAll);
                break;
            case "choice":
                showChoice(m.get("title").get(0), m.get("question").get(0), m.get("button1").get(0), m.get("button2").get(0));
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
                return NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, "accelerometer\ngps\nmicrophone");
        }
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, responseBody);
        return r;
    }

    /**
     * Gets the longitude and latitude of the device
     *
     * @return Longitude and latitude separated by a space
     */
    private String getDeviceLocation() {
        Log.d("LocPerm", "Entered getDeviceLocation() function in HostHandler");

        if (ActivityCompat.checkSelfPermission(service,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(service,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Intent showDialog = new Intent(MainWebView.LOCATION_PERMISSION);
            LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
            Log.d("LocPerm", "Intent Sent to MainWebView");
        }

        if(ActivityCompat.checkSelfPermission(service,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(service,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationManager locationManager =
                    (LocationManager) service.getSystemService(Context.LOCATION_SERVICE);
            Criteria criteria = new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setAltitudeRequired(true);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            String provider = locationManager.getBestProvider(criteria, true);
            locationManager.requestLocationUpdates(provider,
                    LOCATION_UPDATE_MILLIS, LOCATION_UPDATE_THRESHOLD, this);
            Log.d("LocPerm", "HostDeviceHandler reads Location Permissions reads location permissions as true");
            return Double.toString(longitude) + " " + Double.toString(latitude);
        }
        Log.d("LocPerm", "HostDeviceHandler reads Location Permissions reads location permissions as false");
        return "403 Forbidden";
    }



    /**
     * Gets the altitude of the device
     *
     * @return Altitute of device from gps
     */
    private String getDeviceAltitude() {
        return Double.toString(altitude);
    }

    /**
     * Gets the device's wireless SSID, or "null" if there is not one
     *
     * @return Device's SSID or "null" if there is not one
     */
    private String getDeviceSSID() {
        WifiManager wifiManager = (WifiManager) service.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
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
    private String getPressure() {
        return Double.toString(pressure);
    }

    /**
     * Gets the device's acceleration
     *
     * @return Each axis' acceleration separated by spaces
     */
    private String getDeviceAcceleration() {
        return (-deviceAccelX) + " " + (-deviceAccelY) + " " + (-deviceAccelZ);
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
     * @param title        Title of the dialog
     * @param question     Text to show to the user
     * @param hint         Placeholder text for the text input
     * @param defaultText  Default text present in the input text field initially
     * @param selectAll    If this is 'true' (or any variation of the word 'true'),
     *                     the default text in the input box should be pre-selected.
     */
    private void showDialog(String title, String question, String hint, String defaultText, String selectAll) {
        dialogResponse = null;
        // Send broadcast to MainWebView
        Intent showDialog = new Intent(MainWebView.SHOW_DIALOG);
        showDialog.putExtra("type", DialogType.INPUT.toString());
        showDialog.putExtra("title", title);
        showDialog.putExtra("message", question);
        showDialog.putExtra("hint", hint);
        showDialog.putExtra("default", defaultText);
        showDialog.putExtra("select", selectAll.toLowerCase().equals("true"));
        LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
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
        LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
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
        // TODO: Make this behavior identical to iPad
        Display display = ((WindowManager) service
                .getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        int rotation = display.getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                return "Portrait: camera on top";
            case Surface.ROTATION_90:
                return "Landscape: camera on left";
            case Surface.ROTATION_180:
                return "Portrait: camera on bottom";
            case Surface.ROTATION_270:
                return "Landscape: camera on right";
            default:
                return "In Between";
        }
    }

    /**
     * Programatically exits the app
     */
    private void exitApp() {
        Intent showDialog = new Intent(MainWebView.EXIT);
        LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
    }

    @Override
    public void onLocationChanged(Location location) {
        longitude = location.getLongitude();
        latitude = location.getLatitude();
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
                float speed = Math.abs(event.values[0] + event.values[1] + event.values[2] - lastAccelX - lastAccelY - lastAccelZ) / diff * 10000;
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
}
