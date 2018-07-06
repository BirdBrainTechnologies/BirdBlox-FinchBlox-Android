package com.birdbraintechnologies.birdblox.Bluetooth;

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

import com.birdbraintechnologies.birdblox.Robots.RobotType;
import com.birdbraintechnologies.birdblox.Util.NamingHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.connectToRobot;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.hummingbirdsToConnect;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.hummingbitsToConnect;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.lastScanType;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.microbitsToConnect;

/**
 * Helper class for basic Bluetooth connectivity
 *
 * @author Terence Sun (tsun1215)
 */
public class BluetoothHelper {
    private static final int THRESHOLD = 10;
    private static final String TAG = "BluetoothHelper";
    private static final int SCAN_DURATION = 2000;  /* Length of time to perform a scan, in milliseconds */
    public static boolean currentlyScanning;
    private BluetoothAdapter btAdapter;
    private Handler handler;
    private boolean btScanning;
    private Context context;
    public static HashMap<String, BluetoothDevice> deviceList;
    private BluetoothLeScanner scanner;
    private static HashMap<String, Integer> deviceRSSI = new HashMap<>();
    private static ScanSettings scanSettings = (new ScanSettings.Builder())
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build();

    /* Callback for populating the device list */
    private ScanCallback populateDevices = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            synchronized (deviceList) {
                deviceList.put(result.getDevice().getAddress(), result.getDevice());
                List<BluetoothDevice> BLEDeviceList = (new ArrayList<>(deviceList.values()));
                if (lastScanType.equals("hummingbird") && hummingbirdsToConnect != null) {
                    if (hummingbirdsToConnect.contains(result.getDevice().getAddress()) ) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                        }
                        connectToRobot(RobotType.Hummingbird, result.getDevice().getAddress());
                    }
                } else if (lastScanType.equals("hummingbirdbit") && hummingbitsToConnect != null) {
                    if (hummingbitsToConnect.contains(result.getDevice().getAddress()) ) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                        }
                        connectToRobot(RobotType.Hummingbit, result.getDevice().getAddress());
                    }
                } else if (lastScanType.equals("microbit") && microbitsToConnect != null) {
                    if (microbitsToConnect.contains(result.getDevice().getAddress()) ) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                        }
                        connectToRobot(RobotType.Microbit, result.getDevice().getAddress());
                    }
                }

                if (deviceRSSI.get(result.getDevice().getAddress()) == null ||
                        Math.abs(result.getRssi() - deviceRSSI.get(result.getDevice().getAddress())) > THRESHOLD) {
                    deviceRSSI.put(result.getDevice().getAddress(), result.getRssi());
                }

                JSONArray robots = new JSONArray();
                for (BluetoothDevice device : BLEDeviceList) {
                    String name = NamingHandler.GenerateName(mainWebViewContext.getApplicationContext(), device.getAddress());
                    String prefix = "";
                    switch (device.getName().substring(0, 2)) {
                        case "HM":
                            prefix = "Duo";
                            break;
                        case "HB":
                            prefix = "Duo";
                            break;
                        case "FN":
                            prefix = "Finch";
                            break;
                        case "BB":
                            prefix = "Bit";
                            break;
                        case "MB":
                            prefix = "micro:bit";
                            break;
                    }
                    JSONObject robot = new JSONObject();
                    try {
                        robot.put("id", device.getAddress());
                        robot.put("device", prefix);
                        robot.put("name", name);
                        robot.put("RSSI", deviceRSSI.get(device.getAddress()));
                    } catch (JSONException e) {
                        Log.e("JSON", "JSONException while discovering " + lastScanType);
                    }

                    robots.put(robot);
                }
                runJavascript("CallbackManager.robot.discovered('" + lastScanType + "', '" + bbxEncode(robots.toString()) + "');");
            }
        }
    };

    /**
     * Initializes a Bluetooth helper
     *
     * @param context Context that Bluetooth is being used by
     */
    public BluetoothHelper(Context context) {
        this.context = context;
        this.btScanning = false;
        this.handler = new Handler();
        deviceList = new HashMap<>();
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
     * @param scanFilters List of Bluetooth.le.ScanFilter to filter by
     */
    public void scanDevices(List<ScanFilter> scanFilters) {
        Log.d("BLEScan", "About to start scan");
        if (currentlyScanning) {
            Log.d("BLEScan", "Scan already running.");
            return;
        }
        if (scanner == null && btAdapter.isEnabled()) {

            // Start scanning for devices
            scanner = btAdapter.getBluetoothLeScanner();

            btScanning = true;
            // Build scan settings (scan as fast as possible)

            currentlyScanning = true;
            scanner.startScan(scanFilters, scanSettings, populateDevices);
            // Schedule thread to stop scanning after SCAN_DURATION
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    btScanning = false;
                    if (scanner != null) {
                        scanner.stopScan(populateDevices);
                        Log.d("BLEScan", "Stopped scan.");
                        scanner = null;
                    }
                    currentlyScanning = false;
                    runJavascript("CallbackManager.robot.discoverTimeOut('" + lastScanType + "');");
                }
            }, SCAN_DURATION);
        } else {
            currentlyScanning = true;
        }
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
        synchronized (deviceList) {
            device = deviceList.get(addr);
        }

        if (device == null) {
            Log.e(TAG, "Unable to connect to device: " + addr);
            return null;
        }

        UARTConnection conn = new UARTConnection(context, device, settings);
        while (!conn.isConnected()) {
            conn = new UARTConnection(context, device, settings);
        }
        return conn;
    }

    public void stopScan() {
        if (scanner != null) {
            scanner.stopScan(populateDevices);
            scanner = null;
            Log.d("BLEScan", "Stopped scan.");
        }
        if (deviceList != null) {
            deviceList.clear();
        }
        currentlyScanning = false;
    }
}