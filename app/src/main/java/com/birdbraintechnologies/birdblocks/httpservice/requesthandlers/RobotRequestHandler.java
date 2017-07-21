package com.birdbraintechnologies.birdblocks.httpservice.requesthandlers;

import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;
import android.util.Log;

import com.birdbraintechnologies.birdblocks.Robots.Flutter;
import com.birdbraintechnologies.birdblocks.Robots.Hummingbird;
import com.birdbraintechnologies.birdblocks.Robots.Robot;
import com.birdbraintechnologies.birdblocks.Robots.RobotType;
import com.birdbraintechnologies.birdblocks.bluetooth.BluetoothHelper;
import com.birdbraintechnologies.birdblocks.bluetooth.MelodySmartConnection;
import com.birdbraintechnologies.birdblocks.bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblocks.bluetooth.UARTSettings;
import com.birdbraintechnologies.birdblocks.httpservice.HttpService;
import com.birdbraintechnologies.birdblocks.httpservice.RequestHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;

import static com.birdbraintechnologies.birdblocks.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblocks.MainWebView.runJavascript;
import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

/**
 * @author AppyFizz (Shreyan Bakshi)
 */

public class RobotRequestHandler implements RequestHandler {

    private final String TAG = this.getClass().getName();


    /* UUIDs for different Hummingbird features */
    private static final String HB_ROBOT_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final UUID HB_UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID HB_TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID HB_RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    /* UUIDs for different Flutter features */
    private static final String FL_ROBOT_UUID = "BC2F4CC6-AAEF-4351-9034-D66268E328F0";
    private static final UUID FL_UART_UUID = UUID.fromString("BC2F4CC6-AAEF-4351-9034-D66268E328F0");
    private static final UUID FL_TX_UUID = UUID.fromString("06D1E5E7-79AD-4A71-8FAA-373789F7D93C");
    private static final UUID FL_RX_UUID = UUID.fromString("818AE306-9C5B-448D-B51A-7ADD6A5D314D");

    // TODO: Remove this, it is the same across devices
    private static final UUID RX_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    HttpService service;
    private BluetoothHelper btHelper;
    private HashMap<String, Thread> threadMap;

    private UARTSettings HBUARTSettings;
    private HashMap<String, Hummingbird> connectedHummingbirds;

    private UARTSettings FLUARTSettings;
    private HashMap<String, Flutter> connectedFlutters;

    public static String lastScanType;

    public RobotRequestHandler(HttpService service) {
        this.service = service;
        this.btHelper = service.getBluetoothHelper();
        this.threadMap = new HashMap<>();

        this.connectedHummingbirds = new HashMap<>();
        this.connectedFlutters = new HashMap<>();

        // Build Hummingbird UART settings
        this.HBUARTSettings = (new UARTSettings.Builder())
                .setUARTServiceUUID(HB_UART_UUID)
                .setRxCharacteristicUUID(HB_RX_UUID)
                .setTxCharacteristicUUID(HB_TX_UUID)
                .setRxConfigUUID(RX_CONFIG_UUID)
                .build();

        // Build Flutter UART settings
        this.FLUARTSettings = (new UARTSettings.Builder())
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


    // TODO: Properly define Robot Object

    // TODO: Synchronization of below functions

    // TODO: Finish implementing new Robot commands and callbacks


    private String startScan(final RobotType robotType) {
        lastScanType = robotType.toString().toLowerCase();
        if (!BluetoothHelper.currentlyScanning) {
            new Thread() {
                @Override
                public void run() {
                    btHelper.scanDevices(generateDeviceFilter(robotType));
                }
            }.start();
        }
        return "";
    }

    /**
     * Finds a robotId in the list of connected robots. Null if it does not exist.
     *
     * @param robotType The type of the robot to be found. Must be 'hummingbird' or 'flutter'.
     * @param robotId   Robot ID to find.
     * @return The connected Robot if it exists, null otherwise.
     */
    private Robot getRobotFromId(RobotType robotType, String robotId) {
        if (robotType == RobotType.Hummingbird) {
            return connectedHummingbirds.get(robotId);
        } else {
            return connectedFlutters.get(robotId);
        }
    }

    /**
     * Creates a bluetooth scan Robot filter that only matches the required 'type' of Robot.
     *
     * @param robotType The 'type' of Robot to be scanned for (Hummingbird or Flutter).
     * @return List of scan filters.
     */
    private static List<ScanFilter> generateDeviceFilter(RobotType robotType) {
        String ROBOT_UUID = (robotType == RobotType.Hummingbird ? HB_ROBOT_UUID : FL_ROBOT_UUID);
        ScanFilter scanFilter = (new ScanFilter.Builder())
                .setServiceUuid(ParcelUuid.fromString(ROBOT_UUID))
                .build();
        List<ScanFilter> robotFilters = new ArrayList<>();
        robotFilters.add(scanFilter);
        return robotFilters;
    }

    /**
     *
     * @param robotType
     * @param robotId
     * @return
     */
    private String connectToRobot(RobotType robotType, String robotId) {
        // stopDiscover();
        if (robotType == RobotType.Hummingbird) {
            connectToHummingbird(robotId);
        } else {
            connectToFlutter(robotId);
        }
        BluetoothHelper.currentlyScanning = false;
        return "";
    }

    /**
     *
     * @param hummingbirdId
     * @return
     */
    private void connectToHummingbird(final String hummingbirdId) {
        final UARTSettings HBUART = this.HBUARTSettings;
        try {
            Thread hbConnectionThread = new Thread() {
                @Override
                public void run() {
                    UARTConnection hbConn = btHelper.connectToDeviceUART(hummingbirdId, HBUART);
                    if (hbConn != null && connectedHummingbirds != null) {
                        Hummingbird hummingbird = new Hummingbird(hbConn);
                        connectedHummingbirds.put(hummingbirdId, hummingbird);
                    }
                }
            };
            hbConnectionThread.start();
            final Thread oldThread = threadMap.put(hummingbirdId, hbConnectionThread);
            if (oldThread != null) {
                new Thread() {
                    @Override
                    public void run() {
                        super.run();
                        oldThread.interrupt();
                    }
                }.start();
            }
        } catch (Exception e) {
            Log.e("ConnectHB", " Error while connecting to HB " + e.getMessage());
        }
    }

    /**
     * @param FlutterId
     */
    private void connectToFlutter(final String FlutterId) {
        final UARTSettings FLUART = this.FLUARTSettings;
        try {
            Thread flConnectionThread = new Thread() {
                @Override
                public void run() {
                    MelodySmartConnection flConn = btHelper.connectToDeviceMelodySmart(FlutterId, FLUART);
                    if (flConn != null && connectedFlutters != null) {
                        Flutter flutter = new Flutter(flConn);
                        connectedFlutters.put(FlutterId, flutter);
                    }
                }
            };
            flConnectionThread.start();
            final Thread oldThread = threadMap.put(FlutterId, flConnectionThread);
            if (oldThread != null) {
                new Thread() {
                    @Override
                    public void run() {
                        super.run();
                        oldThread.interrupt();
                    }
                }.start();
            }
        } catch (Exception e) {
            Log.e("ConnectFL", " Error while connecting to FL " + e.getMessage());
        }
    }

    /**
     * @param robotType
     * @param robotId
     * @return
     */
    private String disconnectFromRobot(RobotType robotType, final String robotId) {
        new Thread() {
            @Override
            public void run() {
                super.run();
                Thread connThread = threadMap.get(robotId);
                if (connThread != null) connThread.interrupt();
            }
        }.start();

        if (robotType == RobotType.Hummingbird) disconnectFromHummingbird(robotId);
        else disconnectFromFlutter(robotId);

        runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(robotId) + "', false);");

        Log.d("TotStat", "Connected Hummingbirds: " + connectedHummingbirds.toString());
        Log.d("TotStat", "Connected Flutters: " + connectedFlutters.toString());
        return robotType.toString() + " disconnected successfully.";
    }

    /**
     * @param hummingbirdId
     */
    private void disconnectFromHummingbird(String hummingbirdId) {
        try {
            Hummingbird hummingbird = (Hummingbird) getRobotFromId(RobotType.Hummingbird, hummingbirdId);
            if (hummingbird != null) {
                Log.d(TAG, "Disconnecting from hummingbird: " + hummingbirdId);
                if (hummingbird.isConnected())
                    hummingbird.disconnect();
                Log.d("TotStat", "Removing hummingbird: " + hummingbirdId);
                connectedHummingbirds.remove(hummingbirdId);
            }
        } catch (Exception e) {
            Log.e("ConnectHB", " Error while disconnecting from HB " + e.getMessage());
        }
    }

    /**
     * @param flutterId
     */
    private void disconnectFromFlutter(String flutterId) {
        try {
            Flutter flutter = (Flutter) getRobotFromId(RobotType.Flutter, flutterId);
            if (flutter != null) {
                Log.d(TAG, "Disconnecting from flutter: " + flutterId);
                if (flutter.isConnected())
                    flutter.disconnect();
                Log.d("TotStat", "Removing flutter: " + flutterId);
                connectedFlutters.remove(flutterId);
            }
        } catch (Exception e) {
            Log.e("ConnectFL", " Error while disconnecting from FL " + e.getMessage());
        }
    }

    /**
     * @param robotType
     * @return
     */
    private String getTotalStatus(RobotType robotType) {
        return (robotType == RobotType.Hummingbird) ? getTotalHBStatus() : getTotalFLStatus();
    }

    /**
     * @return
     */
    private String getTotalHBStatus() {
        Log.d("TotStat", "Connected Hummingbirds: " + connectedHummingbirds.toString());
        if (connectedHummingbirds.size() == 0) {
            return "2";  // No hummingbirds connected
        }
        for (Hummingbird hummingbird : connectedHummingbirds.values()) {
            if (!hummingbird.isConnected()) {
                return "0";  // Some hummingbird is disconnected
            }
        }
        return "1";  // All hummingbirds are OK
    }

    /**
     * @return
     */
    private String getTotalFLStatus() {
        Log.d("TotStat", "Connected Flutters: " + connectedFlutters.toString());
        if (connectedFlutters.size() == 0) {
            return "2";  // No flutters connected
        }
        for (Flutter flutter : connectedFlutters.values()) {
            if (!flutter.isConnected()) {
                return "0";  // Some flutter is disconnected
            }
        }
        return "1";  // All flutters are OK
    }

    private String stopDiscover() {
        if (btHelper != null)
            btHelper.stopScan();
        runJavascript("CallbackManager.robot.stopDiscover('" + lastScanType + "');");
        return "Bluetooth discovery stopped.";
    }

}
