package com.birdbraintechnologies.birdblocks;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by tsun on 2/12/17.
 */

public class HummingbirdRequestHandler implements RequestHandler {
    private static final String TAG = "HBRequestHandler";
    private static final String HUMMINGBIRD_BTLE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final int SCAN_PERIOD = 500;
    private HttpService service;
    private Handler handler;
    private boolean btScanning;
    private HashSet<BluetoothDevice> deviceList = new HashSet<>();

    private ScanCallback populateDevices = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            deviceList.add(result.getDevice());
        }
    };

    public HummingbirdRequestHandler(HttpService service) {
        this.service = service;
        this.handler = new Handler();
    }


    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        String responseBody = "";
        if (path.length == 1) {
            switch (path[0]) {
                case "discover":
                    responseBody = listDevices();
                    break;
                case "totalStatus":
                    responseBody = getTotalStatus();
                    break;
            }
        }
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }

    private String listDevices() {
        final BluetoothLeScanner scanner = service.getBluetoothAdapter().getBluetoothLeScanner();

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                btScanning = false;
                scanner.stopScan(populateDevices);
            }
        }, SCAN_PERIOD);

        btScanning = true;
        ScanSettings.Builder settingsBuilder = new ScanSettings.Builder();
        settingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);

        ScanFilter.Builder filterBuilder = new ScanFilter.Builder();
        filterBuilder.setServiceUuid(ParcelUuid.fromString(HUMMINGBIRD_BTLE_UUID));

        List<ScanFilter> scanFilters = new ArrayList<>();
        scanFilters.add(filterBuilder.build());

        scanner.startScan(scanFilters, settingsBuilder.build(), populateDevices);
        try {
            while (btScanning) {
                Thread.sleep(SCAN_PERIOD);
            };
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }

        // TODO: Change this behavior to display correctly on device
        String devices = "";
        for (BluetoothDevice device : deviceList) {
            devices = devices + device.getName() + " (" + device.getAddress() + ")\n";
        }
        return devices.trim();
    }

    private String getTotalStatus() {
        return "2";  // TODO: Make this actual total status
    }
}
