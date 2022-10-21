package com.birdbraintechnologies.birdblox.Bluetooth;

import android.Manifest;
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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.birdbraintechnologies.birdblox.BuildConfig;
import com.birdbraintechnologies.birdblox.MainWebView;
import com.birdbraintechnologies.birdblox.Robots.RobotType;
import com.birdbraintechnologies.birdblox.Util.NamingHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.connectToRobot;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.deviceGatt;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.robotsToConnect;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/**
 * Helper class for basic Bluetooth connectivity
 *
 * @author Terence Sun (tsun1215)
 * @author Zhendong Yuan (yzd1998111)
 */
public class BluetoothHelper {
    private static final int THRESHOLD = 20;
    public static final int AUTOCONNECTION_THRESHOLD = -75;
    private static final String TAG = "BluetoothHelper";
    private static final int SCAN_DURATION = 10000;//2000;  /* Length of time to perform a scan, in milliseconds */
    public static boolean currentlyScanning;
    private BluetoothAdapter btAdapter;
    private Handler handler;
    public Context context;
    public static HashMap<String, BluetoothDevice> deviceList;
    private static HashMap<String, BluetoothDevice> discoveredList;
    private BluetoothLeScanner scanner;
    private static HashMap<String, Integer> deviceRSSI = new HashMap<>();
    private static HashMap<String, AtomicLong> deviceLastSeen = new HashMap<>();
    private static ScanSettings scanSettings = (new ScanSettings.Builder())
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build();
    private AtomicLong last_sent = new AtomicLong(System.currentTimeMillis());
    private static final int SEND_INTERVAL = 500;//2000; /* Interval that scan results will be sent to frontend, in milliseconds */

//    private AtomicLong last_clear = new AtomicLong(System.currentTimeMillis());
//    private static final int CLEAR_INTERVAL = 10000; /* Interval that scan results will be cleared from deviceList, in milliseconds */

    /* Callback for populating the device list and discoveredList
       The discoveredList keeps track of all the devices found after a startDiscover request is issued,
       it ensure that the user can connect to the device that can be found in the connection interface.
       The deviceList is cleared for every SEND_INTERVAL to ensure that the user cannot find a device
       that is connected by other users.
    */
    private ScanCallback populateDevices = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            synchronized (deviceList) {
                //Log.d(TAG, "onScanResult " + result.toString());

                BluetoothDevice dev = result.getDevice();
                String macAddress = dev.getAddress();
                String gapName = "UNKNOWN";
                if ((ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                    || (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R)) {
                    gapName = dev.getName();
                }
                int currentRSSI = result.getRssi();

                deviceList.put(macAddress, dev);
                discoveredList.put(macAddress, dev);
                deviceLastSeen.put(macAddress, new AtomicLong(System.currentTimeMillis()));
                List<BluetoothDevice> BLEDeviceList = (new ArrayList<>(deviceList.values()));

                if (robotsToConnect != null) {
                    if (robotsToConnect.contains(gapName)) {
                        if (result.getRssi() < AUTOCONNECTION_THRESHOLD) {
                            robotsToConnect = new HashSet<>(); //TODO: Why?
                        } else {
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Sleep before autoreconnect interrupted: " + e.getMessage());
                            }
                            RobotType robotType = RobotType.robotTypeFromGAPName(gapName);
                            Log.d(TAG, "Reconnecting to " + macAddress + " with name " + gapName + " as type " + robotType.toString());
                            robotsToConnect.remove(gapName);
                            connectToRobot(robotType, macAddress);
                        }
                    }
                }

                if ((deviceRSSI.get(macAddress) == null) ||
                        (Math.abs(result.getRssi() - deviceRSSI.get(macAddress)) > THRESHOLD)) {
                    deviceRSSI.put(macAddress, currentRSSI);
                }

                if (System.currentTimeMillis() - last_sent.get() >= SEND_INTERVAL) {
                    last_sent.set(System.currentTimeMillis());
                    JSONArray robots = new JSONArray();
                    for (BluetoothDevice device : BLEDeviceList) {
                        //Make sure we have seen this device recently
                        if (System.currentTimeMillis() - deviceLastSeen.get(device.getAddress()).get() <= 2000) {
                            String name = NamingHandler.GenerateName(mainWebViewContext.getApplicationContext(), device.getAddress());
                            RobotType robotType = null;
                            if ((ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
                                    || (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R)) {
                                robotType = RobotType.robotTypeFromGAPName(device.getName());
                            }
                            String prefix = (robotType != null) ? robotType.getPrefix() : "";
                            JSONObject robot = new JSONObject();
                            try {
                                robot.put("id", device.getAddress());
                                robot.put("device", prefix);
                                robot.put("name", name);
                                robot.put("RSSI", deviceRSSI.get(device.getAddress()));
                            } catch (JSONException e) {
                                Log.e("JSON", "JSONException while discovering devices");
                            }
                            robots.put(robot);
                        }
                    }
                    Log.d(TAG, "onScanResult sending " + robots.toString());
                    runJavascript("CallbackManager.robot.discovered('" + bbxEncode(robots.toString()) + "');");
                }

                // if (System.currentTimeMillis() - last_clear.get() >= CLEAR_INTERVAL) {
                //     last_clear.set(System.currentTimeMillis());
                //     deviceList.clear();
                // }
            }
        }
    };

    /**
     * Initializes a Bluetooth helper
     *
     * @param context Context that Bluetooth is being used by
     */
    public BluetoothHelper(Context context) {
        Log.e(TAG, "should check bt permissions");
        this.context = context;
        this.handler = new Handler();
        deviceList = new HashMap<>();
        discoveredList = new HashMap<>();
        // Acquire Bluetooth service
        final BluetoothManager btManager =
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.btAdapter = btManager.getAdapter();
        // Ask to enable Bluetooth if disabled
        /*if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(enableBtIntent);
        }*/
    }

    /**
     * Scans for Bluetooth devices that matches the filter.
     *
     * @param scanFilters List of Bluetooth.le.ScanFilter to filter by
     */
    public void scanDevices(List<ScanFilter> scanFilters) {
        Log.d("BLEScan", "About to start scan");
        if (currentlyScanning) {
            Log.d("BLEScan", "Scan already running.");
            return;
        }

        int btPerm = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH);
        int btScanPerm = ActivityCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_SCAN);
        Log.d(TAG, "About to scan. bluetooth " + btPerm + " scan " + btScanPerm + " granted " + PackageManager.PERMISSION_GRANTED);
        if (((Build.VERSION.SDK_INT > Build.VERSION_CODES.R) && (btScanPerm != PackageManager.PERMISSION_GRANTED)) ||
                ((Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) && (btPerm != PackageManager.PERMISSION_GRANTED))) {
            Log.d(TAG, "Bluetooth permission not granted. SDK " + Build.VERSION.SDK_INT);
            Intent getBTPerm = new Intent(MainWebView.BLUETOOTH_PERMISSION);
            LocalBroadcastManager.getInstance(context).sendBroadcast(getBTPerm);
            return;
        }

        if (btAdapter == null) {
            Log.e(TAG, "No bluetooth adapter?");
            return;
        }
        if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(enableBtIntent);
            return;
        }
        if (scanner == null && btAdapter.isEnabled()) {
            Log.d("BLEScan", "No scan running, starting now");
            // Start scanning for devices
            scanner = btAdapter.getBluetoothLeScanner();

            // Build scan settings (scan as fast as possible)
            currentlyScanning = true;
            scanner.startScan(scanFilters, scanSettings, populateDevices);
            // Schedule thread to stop scanning after SCAN_DURATION
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (scanner != null) {
                        if ((ActivityCompat.checkSelfPermission(context,
                                Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                                && (Build.VERSION.SDK_INT > Build.VERSION_CODES.R)) {
                            Log.e("BLEScan", "Attempting to stop a scan when bluetooth scanning permission has not been granted?");
                        } else {
                            scanner.stopScan(populateDevices);
                            Log.d("BLEScan", "Stopped scan (timeout).");
                        }
                        scanner = null;
                    }
                    currentlyScanning = false;
                    runJavascript("CallbackManager.robot.discoverTimeOut();");
                }
            }, SCAN_DURATION);
        } else {
            currentlyScanning = true;
        }
        //Wait a full send interval before sending first results
        last_sent.set(System.currentTimeMillis() + SEND_INTERVAL);
    }

    /**
     * Connects to a device and returns the resulting connection
     *
     * @param addr     MAC Address of the device to connect to
     * @param settings Settings to define the UART connection's TX and RX lines
     * @return Result connection, null if the given MAC Address doesn't match any scanned device
     */
    public UARTConnection connectToDeviceUART(String addr, UARTSettings settings) {
        BluetoothDevice device;
        synchronized (discoveredList) {
            device = discoveredList.get(addr);
        }
        if (device == null) {
            Log.e(TAG, "Unable to connect to device: " + addr);
            return null;
        }
        UARTConnection conn = new UARTConnection(context, device, settings);
        if (!conn.isConnected() && deviceGatt.containsKey(addr)) {
            Log.d(TAG, "disconnect connection failure.");
            runJavascript("CallbackManager.robot.connectionFailure('" + bbxEncode(addr)  + "')");
        }
        if (deviceGatt.containsKey(addr)) {
            deviceGatt.remove(addr);
        }

        return conn;
    }

    /**
     * stopScan stops the scan and clears the cache of the discovered devices.
     */
    public void stopScan() {
        if (scanner != null) {
            if ((ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                    && (Build.VERSION.SDK_INT > Build.VERSION_CODES.R)) {
                Log.e("BLEScan", "Attempting to stop a scan when bluetooth scanning permission has not been granted?");
            } else {
                scanner.stopScan(populateDevices);
            }
            scanner = null;
            Log.d("BLEScan", "Stopped scan.");
        }
        if (deviceList != null) {
            deviceList.clear();
        }
        if (discoveredList != null) {
            discoveredList.clear();
        }
        currentlyScanning = false;
    }
}