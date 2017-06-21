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
 * Helper class for basic bluetooth connectivity
 *
 * @author Terence Sun (tsun1215)
 */
public class BluetoothHelper {
    private static final String TAG = "BluetoothHelper";
    private static final int SCAN_DURATION = 5000;  /* Length of time to perform a scan */

    private BluetoothAdapter btAdapter;
    private Handler handler;
    private boolean btScanning;
    private Context context;
    public static HashMap<String, BluetoothDevice> deviceList;
    private BluetoothLeScanner scanner;

    /* Callback for populating the device list */
    private ScanCallback populateDevices = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            synchronized (deviceList) {
                deviceList.put(result.getDevice().getAddress(), result.getDevice());
            }
        }
    };

    /**
     * Initializes a Bluetooth helper
     *
     * @param context Context that bluetooth is being used by
     */
    public BluetoothHelper(Context context) {
        this.context = context;
        this.btScanning = false;
        this.handler = new Handler();
        this.deviceList = new HashMap<>();

        // Acquire Bluetooth service
        final BluetoothManager btManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.btAdapter = btManager.getAdapter();

        // Ask to enable Bluetooth if disabled
        if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(enableBtIntent);
        }
    }

    /**
     * Scans for Bluetooth devices that matches the filter.
     *
     * @param scanFilters List of bluetooth.le.ScanFilter to filter by
     * @return List of devices that matches the filters
     */
    synchronized public List<BluetoothDevice> scanDevices(List<ScanFilter> scanFilters) {
        if (scanner == null) {
            // Start scanning for devices
            scanner = btAdapter.getBluetoothLeScanner();
            // Schedule thread to stop scanning after SCAN_DURATION
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    btScanning = false;
                    scanner.stopScan(populateDevices);
                    scanner = null;
                }
            }, SCAN_DURATION);
            btScanning = true;
            // Build scan settings (scan as fast as possible)
            ScanSettings scanSettings = (new ScanSettings.Builder())
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            scanner.startScan(scanFilters, scanSettings, populateDevices);
        }

        synchronized (deviceList) {
            return new ArrayList<>(deviceList.values());
        }
    }

    /**
     * Connects to a device and returns the resulting connection
     *
     * @param addr     MAC Address of the device to connect to
     * @param settings Settings to define the UART connection's TX and RX lines
     * @return Result connection, null if the given MAC Address doesn't match any scanned device
     */
    synchronized public UARTConnection connectToDeviceUART(String addr, UARTSettings settings) {
        BluetoothDevice device = deviceList.get(addr);
        if (device == null) {
            Log.e(TAG, "Unable to connect to device: " + addr);
            return null;
        }

        UARTConnection conn = new UARTConnection(context, device, settings);

        return conn;
    }

    /**
     * Connects to a device and returns the resulting connection
     *
     * @param addr     MAC Address of the device to connect to
     * @param settings Settings to define the UART connection's TX and RX lines
     * @return Result connection, null if the given MAC Address doesn't match any scanned device
     */
    synchronized public MelodySmartConnection connectToDeviceMelodySmart(String addr, UARTSettings settings) {
        BluetoothDevice device = deviceList.get(addr);
        if (device == null) {
            Log.e(TAG, "Unable to connect to device: " + addr);
            return null;
        }

        MelodySmartConnection conn = new MelodySmartConnection(context, device, settings);

        return conn;
    }

    public void stopScan() {
        if (scanner != null)
            scanner.stopScan(populateDevices);
        if (deviceList != null)
            deviceList.clear();
    }

}
