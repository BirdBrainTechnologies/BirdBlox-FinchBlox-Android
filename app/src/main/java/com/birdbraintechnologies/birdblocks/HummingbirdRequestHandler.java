package com.birdbraintechnologies.birdblocks;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by tsun on 2/12/17.
 */

public class HummingbirdRequestHandler implements RequestHandler {
    private static final String TAG = "HBRequestHandler";
    private static final String HUMMINGBIRD_DEVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private final static UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    private HttpService service;
    private UARTSettings hbUARTSettings;
    private BluetoothHelper btHelper;
    private HashMap<String, UARTConnection> connectedDevices;


    public HummingbirdRequestHandler(HttpService service) {
        this.service = service;
        this.btHelper = service.getBluetoothHelper();
        this.connectedDevices = new HashMap<>();

        this.hbUARTSettings = (new UARTSettings.Builder())
                .setUARTServiceUUID(UART_UUID)
                .setRxCharacteristicUUID(RX_UUID)
                .setTxCharacteristicUUID(TX_UUID)
                .build();
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
        } else {
            switch (path[1]) {
                case "connect":
                    responseBody = connectToDevice(path[0]);
                    break;
                case "disconnect":
                    responseBody = disconnectFromDevice(path[0]);
                    break;
            }
        }
        NanoHTTPD.Response r = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, responseBody);
        return r;
    }

    private String listDevices() {
        List<BluetoothDevice> deviceList = btHelper.scanDevices(generateDeviceFilter());
        // TODO: Change this behavior to display correctly on device
        String devices = "";
        for (BluetoothDevice device : deviceList) {
            devices = devices + device.getName() + " (" + device.getAddress() + ")\n";
        }
        return devices.trim();
    }

    private String connectToDevice(String deviceId) {
        Matcher match = Pattern.compile("^.*\\(([\\w\\:]+)\\)").matcher(deviceId);

        if (match.matches()) {
            String deviceMAC = match.group(1);

            Log.d(TAG, "Connecting to device: " + deviceMAC);

            UARTConnection conn = btHelper.connectToDeviceUART(deviceMAC, this.hbUARTSettings);
            connectedDevices.put(deviceMAC, conn);
        }

        return "";
    }

    private String disconnectFromDevice(String deviceId) {
        Matcher match = Pattern.compile("^.*\\(([\\w\\:]+)\\)").matcher(deviceId);

        if (match.matches()) {
            String deviceMAC = match.group(1);

            Log.d(TAG, "Disconnecting from device: " + deviceMAC);

            UARTConnection conn = connectedDevices.remove(deviceMAC);
            if (conn != null) {
                conn.disconnect();
            }
        }

        return "";
    }

    /**
     * Creatse a bluetooth scan device filter that only matches Hummingbird devices
     *
     * @return List of scan filters
     */
    private List<ScanFilter> generateDeviceFilter() {
        ScanFilter hbScanFilter = (new ScanFilter.Builder())
                .setServiceUuid(ParcelUuid.fromString(HUMMINGBIRD_DEVICE_UUID))
                .build();
        List<ScanFilter> hummingbirdDeviceFilters = new ArrayList<>();
        hummingbirdDeviceFilters.add(hbScanFilter);

        return hummingbirdDeviceFilters;
    }

    private String getTotalStatus() {
        if (connectedDevices.size() == 0) {
            return "2";  // No devices connected
        }
        for (UARTConnection conn : connectedDevices.values()) {
            if (!conn.isConnected()) {
                return "0";
            }
        }
        return "1";  // All devices are OK
    }
}
