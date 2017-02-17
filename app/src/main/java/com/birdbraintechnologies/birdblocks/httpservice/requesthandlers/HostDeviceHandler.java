package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.Manifest;
import android.content.Context;
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
import android.util.Log;

import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by tsun on 2/17/17.
 */

public class HostDeviceHandler implements RequestHandler, LocationListener, SensorEventListener {
    private static final String TAG = HostDeviceHandler.class.getName();
    private static final int LOCATION_UPDATE_MILLIS = 100;
    private static final float LOCATION_UPDATE_THRESHOLD = 0.0f;  // in meters
    HttpService service;

    private double longitude, latitude, altitude, pressure;

    public HostDeviceHandler(HttpService service) {
        this.service = service;
        initLocationListener();
        initSensors();
    }

    private void initSensors() {
        SensorManager manager = (SensorManager) service.getSystemService(Context.SENSOR_SERVICE);
        manager.registerListener(this, manager.getDefaultSensor(Sensor.TYPE_PRESSURE),
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
        WifiManager wifiManager = (WifiManager) service.getSystemService (Context.WIFI_SERVICE);
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
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
