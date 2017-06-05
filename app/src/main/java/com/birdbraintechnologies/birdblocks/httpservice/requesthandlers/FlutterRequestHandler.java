package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.bluetooth.BluetoothHelper;
import com.birdbraintechnologies.birdblocks.bluetooth.MelodySmartConnection;
import com.birdbraintechnologies.birdblocks.bluetooth.UARTSettings;
import com.birdbraintechnologies.birdblocks.devices.Flutter;
import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;
import com.birdbraintechnologies.birdblocks.util.NamingHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;

/**
 * Class for handling requests from the router to Flutter devices
 *
 * @author Terence Sun (tsun1215)
 * @author Shreyan Bakshi (AppyFizz)
 */
public class FlutterRequestHandler implements RequestHandler {
    private static final String TAG = FlutterRequestHandler.class.getName();

    /* UUIDs for different Flutter features */
    private static final String FLUTTER_DEVICE_UUID = "BC2F4CC6-AAEF-4351-9034-D66268E328F0";
    private static final UUID UART_UUID = UUID.fromString("BC2F4CC6-AAEF-4351-9034-D66268E328F0");
    private static final UUID TX_UUID = UUID.fromString("06D1E5E7-79AD-4A71-8FAA-373789F7D93C");
    private static final UUID RX_UUID = UUID.fromString("818AE306-9C5B-448D-B51A-7ADD6A5D314D");
    // TODO: Remove this, it is the same across devices
    private static final UUID RX_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private HttpService service;
    private UARTSettings flutterUARTSettings;
    private BluetoothHelper btHelper;
    private HashMap<String, Flutter> connectedDevices;


    public FlutterRequestHandler(HttpService service) {
        this.service = service;
        this.btHelper = service.getBluetoothHelper();
        this.connectedDevices = new HashMap<>();

        // Build UART settings
        this.flutterUARTSettings = (new UARTSettings.Builder())
                .setUARTServiceUUID(UART_UUID)
                .setRxCharacteristicUUID(RX_UUID)
                .setTxCharacteristicUUID(TX_UUID)
                .setRxConfigUUID(RX_CONFIG_UUID)
                .build();
    }

    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        // Generate response body
        String responseBody = "";
        switch (path[0]) {
            case "discover":
                responseBody = listDevices();
                Log.d("DNameFlutter", "Discover Flutters");
                Log.d("BLEIssue", "Discovered Flutters: " + listDevices());
                Log.d("BLEIssue", "Connected Flutters: " + connectedDevices.toString());
                break;
            case "stopDiscover":
                Log.d("DiscFlutt", "Stop Discover");
                responseBody = stopDiscover();
                Log.d("BLEIssue", "Discovered Flutters: " + listDevices());
                Log.d("BLEIssue", "Connected Flutters: " + connectedDevices.toString());
                break;
            case "totalStatus":
                responseBody = getTotalStatus();
                break;
            case "connect":
                responseBody = connectToDevice(m.get("name").get(0));
                Log.d("BLEIssue", "Discovered Flutters: " + listDevices());
                Log.d("BLEIssue", "Connected Flutters: " + connectedDevices.toString());
                break;
            case "disconnect":
                responseBody = disconnectFromDevice(m.get("name").get(0));
                Log.d("BLEIssue", "Discovered Flutters: " + listDevices());
                Log.d("BLEIssue", "Connected Flutters: " + connectedDevices.toString());
                break;
            case "out":
                getDeviceFromId(m.get("name").get(0)).setOutput(path[1], m);
                responseBody = "Connected to Flutter successfully.";
                break;
            case "in":
                responseBody = getDeviceFromId(m.get("name").get(0)).readSensor(m.get("sensor").get(0), m.get("port").get(0));
                break;
//            case "rename":
//                responseBody = renameDevice(path[0], path[2]);
//                break;
        }

        // Create response from the responseBody
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }


    /**
     * Lists the Flutter devices that are seen
     *
     * @return List of Flutter devices
     */
    private String listDevices() {
        List<BluetoothDevice> deviceList = btHelper.scanDevices(generateDeviceFilter());
        // TODO: Change this behavior to display correctly on device
        JSONArray devices = new JSONArray();
        for (BluetoothDevice device : deviceList) {
            String name = NamingHandler.GenerateName(service.getApplicationContext(), device.getAddress());
            Log.d("DNameFlutter", name);
            JSONObject flutt = new JSONObject();
            try {
                flutt.put("id", device.getAddress());
                flutt.put("name", name);
            } catch (JSONException e) {
                Log.e("JSON", "JSONException while discovering flutters");
            }
            devices.put(flutt);
        }
        Log.d("FluttLogList", "List: " + devices.toString());
        return devices.toString();
    }

    /**
     * Creates a bluetooth scan device filter that only matches Flutter devices
     *
     * @return List of scan filters
     */
    private static List<ScanFilter> generateDeviceFilter() {
        ScanFilter flScanFilter = (new ScanFilter.Builder())
                .setServiceUuid(ParcelUuid.fromString(FLUTTER_DEVICE_UUID))
                .build();
        List<ScanFilter> flutterDeviceFilters = new ArrayList<>();
        flutterDeviceFilters.add(flScanFilter);

        return flutterDeviceFilters;
    }

    /**
     * Finds a deviceId in the list of connected devices. Null if it does not exist.
     *
     * @param deviceId Device ID to find
     * @return The connected device if it exists, null otherwise
     */
    private Flutter getDeviceFromId(String deviceId) {
        return connectedDevices.get(deviceId);
    }

    /**
     * Connects to the device and creates a Flutter instance in the list of connected devices
     *
     * @param deviceId Device ID to connect to
     * @return No Response
     */
    private String connectToDevice(String deviceId) {
        String deviceMAC = deviceId;
        // TODO: Handle errors when connecting to device
        MelodySmartConnection conn = btHelper.connectToDeviceMelodySmart(deviceMAC, this.flutterUARTSettings);
        if (conn != null) {
            Flutter device = new Flutter(conn);
            connectedDevices.put(deviceMAC, device);
        }
        return "";
    }

    /**
     * Renames a device
     * @param deviceId Device ID to rename
     * @param newName New name to give to the device
     * @return No Response
     */
//    private String renameDevice(String deviceId, String newName) {
//        Log.e(TAG, "Call to deprecated function: renameDevice");
//        return "";
//    }


    /**
     * Disconnects from a given device
     *
     * @param deviceId Device ID of the device to disconnect from
     * @return No Response
     */
    private String disconnectFromDevice(String deviceId) {
        Flutter device = getDeviceFromId(deviceId);
        if (device != null) {
            Log.d(TAG, "Disconnecting from device: " + deviceId);
            device.disconnect();
            connectedDevices.remove(deviceId);
        }
        return "";
    }

    /**
     * Returns the aggregated status of all connected Flutters. 0 means at least 1 device is
     * disconnected; 1 means that all devices are OK, 2 means that no devices are connected.
     *
     * @return 0, 1, or 2 depending on the aggregate status of all the devices
     */
    private String getTotalStatus() {
        if (connectedDevices.size() == 0) {
            return "2";  // No devices connected
        }
        for (Flutter device : connectedDevices.values()) {
            if (!device.isConnected()) {
                return "0";  // Some device is disconnected
            }
        }
        return "1";  // All devices are OK
    }

    /**
     *
     *
     */
    private String stopDiscover() {
        // btHelper.stopScan();
        return "Bluetooth discovery stopped.";
    }

}
