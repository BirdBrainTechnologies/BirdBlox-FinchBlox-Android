package com.birdbraintechnologies.birdblocks;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.List;

import fi.iki.elonen.NanoHTTPD;

/**
 * Created by tsun on 2/12/17.
 */

public class HummingbirdRequestHandler implements RequestHandler {
    private static final String TAG = "HBRequestHandler";
    private static final String HUMMINGBIRD_DEVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private HttpService service;
    private BluetoothHelper btHelper;


    public HummingbirdRequestHandler(HttpService service) {
        this.service = service;
        this.btHelper = service.getBluetoothHelper();
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
        List <BluetoothDevice> deviceList = btHelper.scanDevices(generateDeviceFilter());
        // TODO: Change this behavior to display correctly on device
        String devices = "";
        for (BluetoothDevice device : deviceList) {
            devices = devices + device.getName() + " (" + device.getAddress() + ")\n";
        }
        return devices.trim();
    }

    /**
     * Creatse a bluetooth scan device filter that only matches Hummingbird devices
     * @return List of scan filters
     */
    private List<ScanFilter> generateDeviceFilter() {
        ScanFilter.Builder filterBuilder = new ScanFilter.Builder();
        filterBuilder.setServiceUuid(ParcelUuid.fromString(HUMMINGBIRD_DEVICE_UUID));
        List<ScanFilter> hummingbirdDeviceFilter = new ArrayList<>();
        hummingbirdDeviceFilter.add(filterBuilder.build());

        return hummingbirdDeviceFilter;
    }

    private String getTotalStatus() {
        return "2";  // TODO: Make this actual total status
    }
}
