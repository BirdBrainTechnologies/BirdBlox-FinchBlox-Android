package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

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

import com.birdbraintechnologies.birdblocks.MainWebView;
import com.birdbraintechnologies.birdblocks.dialogs.BirdblocksDialog;
import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by tsun on 2/17/17.
 */

public class HostDeviceHandler implements RequestHandler, LocationListener, SensorEventListener {
    private static final String TAG = HostDeviceHandler.class.getName();
    private static final int LOCATION_UPDATE_MILLIS = 100;
    private static final float LOCATION_UPDATE_THRESHOLD = 0.0f;  // in meters
    private static final int FORCE_THRESHOLD = 350;  // How forceful a shake movement needs to be
    private static final int SAMPLE_THRESHOLD = 100;  // Threshold for detecting shake events
    private static final int SHAKE_TIMEOUT = 500;  // Timeout between movements to count as a shake
    private static final int SHAKE_DURATION = 1000;  // Duration between shake events
    private static final int SHAKE_COUNT = 3;  // Number of successful shakes to count as "shaken"

    /* For shake detection */
    private long lastForcefulMovement, lastShake, lastSampleTime;
    private float lastAccelX = -1.0f, lastAccelY = -1.0f, lastAccelZ = -1.0f;
    private int shakeCount;

    HttpService service;
    private double longitude, latitude, altitude, pressure;
    private boolean shaken = false;

    /* For dialogs */
    public static final String DIALOG_RESPONSE = "com.birdbraintechnologies.birdblocks.DIALOG_RESPONSE";
    private String dialogResponse = null;
    private BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(DIALOG_RESPONSE)) {
                dialogResponse = intent.getStringExtra("response");
            }
        }
    };
    LocalBroadcastManager bManager;

    public HostDeviceHandler(HttpService service) {
        this.service = service;
        initLocationListener();
        initSensors();

        bManager = LocalBroadcastManager.getInstance(service);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DIALOG_RESPONSE);
        bManager.registerReceiver(bReceiver, intentFilter);
    }

    private void initSensors() {
        SensorManager manager = (SensorManager) service.getSystemService(Context.SENSOR_SERVICE);
        manager.registerListener(this, manager.getDefaultSensor(Sensor.TYPE_PRESSURE),
                SensorManager.SENSOR_DELAY_UI);
        manager.registerListener(this, manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_UI);
    }

    private void initLocationListener() {
        LocationManager locationManager =
                (LocationManager) service.getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(service,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(service,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Unable to obtain location");
            // TODO: Handler error
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
        switch (path[0]) {
            case "shake":
                responseBody = getShaken();
                break;
            case "location":
                responseBody = getDeviceLocation();
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
            case "dialog":
                showDialog(path[1], path[2], path[3]);
                break;
            case "choice":
                showChoice(path[1], path[2], path[3], path[4]);
                break;
            case "dialog_response":
                responseBody = getDialogResponse();
                break;
            case "choice_response":
                responseBody = getChoiceResponse();
                break;
        }
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }

    private String getDeviceLocation() {
        return Double.toString(longitude) + " " + Double.toString(latitude);
    }

    private String getDeviceAltitude() {
        return Double.toString(altitude);
    }

    private String getDeviceSSID() {
        WifiManager wifiManager = (WifiManager) service.getSystemService(Context.WIFI_SERVICE);
        WifiInfo info = wifiManager.getConnectionInfo();
        String result = info.getSSID();
        result = result.replace("\"", "");
        if (result.trim().equals("<unknown ssid>"))
            result = "null";
        return result;
    }

    private String getPressure() {
        return Double.toString(pressure);
    }

    private String getShaken() {
        if (shaken) {
            shaken = false;
            return "1";
        } else {
            return "0";
        }
    }

    private void showDialog(String title, String question, String hint) {
        dialogResponse = null;
        Intent showDialog = new Intent(MainWebView.SHOW_DIALOG);
        showDialog.putExtra("type", BirdblocksDialog.DialogType.INPUT.toString());
        showDialog.putExtra("title", title);
        showDialog.putExtra("message", question);
        showDialog.putExtra("hint", hint);
        LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
    }

    private void showChoice(String title, String question, String option1, String option2) {
        dialogResponse = null;
        Intent showDialog = new Intent(MainWebView.SHOW_DIALOG);
        showDialog.putExtra("type", BirdblocksDialog.DialogType.CHOICE.toString());
        showDialog.putExtra("title", title);
        showDialog.putExtra("message", question);
        showDialog.putExtra("button1", option1);
        showDialog.putExtra("button2", option2);
        LocalBroadcastManager.getInstance(service).sendBroadcast(showDialog);
    }

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

    private String getDeviceOrientation() {
        Display display = ((WindowManager) service
                .getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();
        int rotation = display.getRotation();
        switch (rotation) {
            case Surface.ROTATION_0:
                return "Portrait: home button on bottom";
            case Surface.ROTATION_90:
                return "Landscape: home button on left";
            case Surface.ROTATION_180:
                return "Portrait: home button on top";
            case Surface.ROTATION_270:
                return "Landscape: home button on right";
            default:
                return "In Between";
        }
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
            long now = System.currentTimeMillis();

            if ((now - lastForcefulMovement) > SHAKE_TIMEOUT) {
                shakeCount = 0;
            }

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
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
