package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import com.birdbraintechnologies.birdblocks.Robots.Flutter;
import com.birdbraintechnologies.birdblocks.Robots.Hummingbird;
import com.birdbraintechnologies.birdblocks.Robots.Robot;
import com.birdbraintechnologies.birdblocks.bluetooth.BluetoothHelper;
import com.birdbraintechnologies.birdblocks.bluetooth.UARTSettings;
import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;

import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

/**
 * @author AppyFizz (Shreyan Bakshi)
 */

public class RobotRequestHandler implements RequestHandler {

    private final String TAG = this.getClass().getName();

    /* UUIDs for different Hummingbird features */
    private static final String HB_DEVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final UUID HB_UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID HB_TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID HB_RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    /* UUIDs for different Flutter features */
    private static final String FL_DEVICE_UUID = "BC2F4CC6-AAEF-4351-9034-D66268E328F0";
    private static final UUID FL_UART_UUID = UUID.fromString("BC2F4CC6-AAEF-4351-9034-D66268E328F0");
    private static final UUID FL_TX_UUID = UUID.fromString("06D1E5E7-79AD-4A71-8FAA-373789F7D93C");
    private static final UUID FL_RX_UUID = UUID.fromString("818AE306-9C5B-448D-B51A-7ADD6A5D314D");

    // TODO: Remove this, it is the same across devices
    private static final UUID RX_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    HttpService service;
    private BluetoothHelper btHelper;
    private HashMap<String, Thread> threadMap;

    private UARTSettings hbUARTSettings;
    private HashMap<String, Hummingbird> connectedHummingbirds;

    private UARTSettings flutterUARTSettings;
    private HashMap<String, Flutter> connectedFlutters;

    public String lastScanType;

    public RobotRequestHandler(HttpService service) {
        this.service = service;
        this.btHelper = service.getBluetoothHelper();
        this.threadMap = new HashMap<>();

        this.connectedHummingbirds = new HashMap<>();
        this.connectedFlutters = new HashMap<>();

        // Build Hummingbird UART settings
        this.hbUARTSettings = (new UARTSettings.Builder())
                .setUARTServiceUUID(HB_UART_UUID)
                .setRxCharacteristicUUID(HB_RX_UUID)
                .setTxCharacteristicUUID(HB_TX_UUID)
                .setRxConfigUUID(RX_CONFIG_UUID)
                .build();

        // Build Flutter UART settings
        this.flutterUARTSettings = (new UARTSettings.Builder())
                .setUARTServiceUUID(FL_UART_UUID)
                .setRxCharacteristicUUID(FL_RX_UUID)
                .setTxCharacteristicUUID(FL_TX_UUID)
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
            case "startDiscover":
                break;
            case "totalStatus":
                break;
            case "stopDiscover":
                break;
            case "connect":
                break;
            case "disconnect":
                break;
            case "out":
                break;
            case "in":
                break;
        }

        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad Request");
    }

    /**
     * Finds a robotId in the list of connected robots. Null if it does not exist.
     *
     * @param robotType The type of the robot to be found. Must be 'hummingbird' or 'flutter'
     * @param robotId
     * @return
     */
    private synchronized Robot getRobotFromId(String robotType, String robotId) {
        return null;
    }


}
