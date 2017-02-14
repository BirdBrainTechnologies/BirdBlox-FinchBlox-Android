package com.birdbraintechnologies.birdblocks.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by tsun on 2/13/17.
 */

public class BluetoothHelper {
    private static final String TAG = "BluetoothHelper";
    private static final int SCAN_PERIOD = 400;

    private BluetoothAdapter btAdapter;
    private Handler handler;
    private boolean btScanning;
    private Context context;
    private HashMap<String, BluetoothDevice> deviceList;
    private ScanCallback populateDevices = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            synchronized (deviceList) {
                deviceList.put(result.getDevice().getAddress(), result.getDevice());
            }
        }
    };

    public BluetoothHelper(Context context) {
        this.context = context;
        this.btScanning = false;
        this.handler = new Handler();
        this.deviceList = new HashMap<>();

        // Acquire bluetooth
        final BluetoothManager btManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.btAdapter = btManager.getAdapter();

        // Enable bt if disabled
        if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(enableBtIntent);
        }
    }

    synchronized public List<BluetoothDevice> scanDevices(List<ScanFilter> scanFilters) {
        final BluetoothLeScanner scanner = btAdapter.getBluetoothLeScanner();

        // Schedule thread to stop scanning after SCAN_PERIOD
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                btScanning = false;
                scanner.stopScan(populateDevices);
            }
        }, SCAN_PERIOD);

        // Start scanning for devices
        btScanning = true;

        ScanSettings scanSettings = (new ScanSettings.Builder())
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        scanner.startScan(scanFilters, scanSettings, populateDevices);

        // Wait until scanning is complete
        try {
            while (btScanning) {
                Thread.sleep(SCAN_PERIOD);
            }
            ;
        } catch (InterruptedException e) {
            Log.e(TAG, e.toString());
        }
        return new ArrayList<>(deviceList.values());
    }

    synchronized public UARTConnection connectToDeviceUART(String addr, UARTSettings settings) {
        BluetoothDevice device = deviceList.get(addr);
        if (device == null) {
            return null;
        }

        UARTConnection conn = new UARTConnection(context, device, settings);

        return conn;
    }
}
