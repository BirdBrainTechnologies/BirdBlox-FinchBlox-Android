package com.birdbraintechnologies.birdblox.httpservice.RequestHandlers;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import com.birdbraintechnologies.birdblox.Bluetooth.BluetoothHelper;
import com.birdbraintechnologies.birdblox.Bluetooth.MelodySmartConnection;
import com.birdbraintechnologies.birdblox.Bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblox.Bluetooth.UARTSettings;
import com.birdbraintechnologies.birdblox.Robots.Flutter;
import com.birdbraintechnologies.birdblox.Robots.Hummingbird;
import com.birdbraintechnologies.birdblox.Robots.Robot;
import com.birdbraintechnologies.birdblox.Robots.RobotType;
import com.birdbraintechnologies.birdblox.Util.NamingHandler;
import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;

import static com.birdbraintechnologies.birdblox.Bluetooth.BluetoothHelper.deviceList;
import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.Robots.RobotType.robotTypeFromString;
import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

/**
 * @author AppyFizz (Shreyan Bakshi)
 */

public class RobotRequestHandler implements RequestHandler {
    private final String TAG = this.getClass().getName();

    private static final String FIRMWARE_UPDATE_URL = "http://www.hummingbirdkit.com/learning/installing-birdblox#BurnFirmware";


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

    private AlertDialog.Builder builder;
    private AlertDialog robotInfoDialog;

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
                responseBody = startScan(robotTypeFromString(m.get("type").get(0)));
                break;
            case "stopDiscover":
                responseBody = stopDiscover();
                break;
            case "totalStatus":
                responseBody = getTotalStatus(robotTypeFromString(m.get("type").get(0)));
                break;
            case "connect":
                responseBody = connectToRobot(robotTypeFromString(m.get("type").get(0)), m.get("id").get(0));
                break;
            case "disconnect":
                responseBody = disconnectFromRobot(robotTypeFromString(m.get("type").get(0)), m.get("id").get(0));
                break;
            case "out":
                Robot robot = getRobotFromId(robotTypeFromString(m.get("type").get(0)), m.get("id").get(0));
                if (robot == null) {
                    runJavascript("CallbackManager.robot.updateStatus('" + m.get("id").get(0) + "', false);");
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Robot " + m.get("id").get(0) + " was not found.");
                } else if (!robot.setOutput(path[1], m)) {
                    runJavascript("CallbackManager.robot.updateStatus('" + m.get("id").get(0) + "', false);");
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.EXPECTATION_FAILED, MIME_PLAINTEXT, "Failed to send to robot " + m.get("id").get(0) + ".");
                } else {
                    runJavascript("CallbackManager.robot.updateStatus('" + m.get("id").get(0) + "', true);");
                    responseBody = "Sent to robot " + m.get("type").get(0) + " successfully.";
                }
                break;
            case "in":
                robot = getRobotFromId(robotTypeFromString(m.get("type").get(0)), m.get("id").get(0));
                if (robot == null) {
                    runJavascript("CallbackManager.robot.updateStatus('" + m.get("id").get(0) + "', false);");
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Robot " + m.get("id").get(0) + " was not found.");
                } else {
                    String sensorValue = robot.readSensor(m.get("sensor").get(0), m.get("port").get(0));
                    if (sensorValue == null) {
                        runJavascript("CallbackManager.robot.updateStatus('" + m.get("id").get(0) + "', false);");
                        return NanoHTTPD.newFixedLengthResponse(
                                NanoHTTPD.Response.Status.NO_CONTENT, MIME_PLAINTEXT, "Failed to read sensors from robot " + m.get("id").get(0) + ".");
                    } else {
                        runJavascript("CallbackManager.robot.updateStatus('" + m.get("id").get(0) + "', true);");
                        responseBody = sensorValue;
                    }
                }
                break;
            case "showInfo":
                responseBody = showRobotInfo(robotTypeFromString(m.get("type").get(0)), m.get("id").get(0));
                break;
            case "showUpdateInstructions":
                showFirmwareUpdateInstructions();
                break;
            case "stopAll":
                stopAll();
                break;
        }

        return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, responseBody);
    }


    // TODO: Properly define Robot Object

    // TODO: Synchronization of below functions

    // TODO: Finish implementing new Robot commands and callbacks


    private String startScan(final RobotType robotType) {
        // TODO: Handle error in this case
        if (robotType == null)
            return "";
        if (BluetoothHelper.currentlyScanning && lastScanType.equals(robotType.toString().toLowerCase()))
            return "";
        if (BluetoothHelper.currentlyScanning) {
            stopDiscover();
        }
        new Thread() {
            @Override
            public void run() {
                btHelper.scanDevices(generateDeviceFilter(robotType));
            }
        }.start();
        lastScanType = robotType.toString().toLowerCase();
        return "";
    }

    /**
     * Lists the Robots (of a given type) that are seen
     *
     * @return List of Robots of the given type
     */
    private synchronized String listRobots(final RobotType robotType) {
        if (!BluetoothHelper.currentlyScanning) {
            new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    btHelper.scanDevices(generateDeviceFilter(robotType));
                }
            });
        }
        List<BluetoothDevice> BLEDeviceList = (new ArrayList<>(deviceList.values()));
        JSONArray robots = new JSONArray();
        for (BluetoothDevice device : BLEDeviceList) {
            String name = NamingHandler.GenerateName(service.getApplicationContext(), device.getAddress());
            JSONObject robot = new JSONObject();
            try {
                robot.put("id", device.getAddress());
                robot.put("name", name);
            } catch (JSONException e) {
                Log.e("JSON", "JSONException while discovering " + lastScanType);
            }
            robots.put(robot);
        }
        runJavascript("CallbackManager.robot.discovered('" + lastScanType + "', '" + bbxEncode(robots.toString()) + "');");
        return robots.toString();
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
     * Creates a Bluetooth scan Robot filter that only matches the required 'type' of Robot.
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
//                else
//                    hummingbird.connectionBroke();
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

    private String showRobotInfo(RobotType robotType, String robotId) {
        builder = new AlertDialog.Builder(mainWebViewContext);

        // Get details
        Robot robot = getRobotFromId(robotType, robotId);
        String name = robot.getName();
        String macAddress = robot.getMacAddress();
        String gapName = robot.getGAPName();
        String hardwareVersion = (robotType == RobotType.Hummingbird) ? ((Hummingbird) robot).getHardwareVersion() : null;
        String firmwareVersion = (robotType == RobotType.Hummingbird) ? ((Hummingbird) robot).getFirmwareVersion() : null;

        builder.setTitle(robotType.toString() + " Peripheral");
        String message = "";
        if (name != null)
            message += ("Name: " + name + "\n");
        if (macAddress != null)
            message += ("MAC Address: " + macAddress + "\n");
        if (gapName != null)
            message += ("Bluetooth Name: " + gapName + "\n");
        if (hardwareVersion != null)
            message += ("Hardware Version: " + hardwareVersion + "\n");
        if (firmwareVersion != null)
            message += ("Firmware Version: " + firmwareVersion + "\n");
        if (!robot.hasLatestFirmware())
            message += ("\nFirmware update available.");
        builder.setMessage(message);
        builder.setCancelable(true);
        if (!robot.hasLatestFirmware()) {
            builder.setPositiveButton(
                    "Update Firmware",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            showFirmwareUpdateInstructions();
                        }
                    });
            builder.setNegativeButton(
                    "Dismiss",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
        } else {
            builder.setNeutralButton(
                    "Dismiss",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                        }
                    });
        }
        new Thread() {
            @Override
            public void run() {
                super.run();
                new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        robotInfoDialog = builder.create();
                        robotInfoDialog.show();
                    }
                });
            }
        }.start();
        return "Successfully showed robot info.";
    }

    private static void showFirmwareUpdateInstructions() {
        mainWebViewContext.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(FIRMWARE_UPDATE_URL)));
    }

    /**
     * Resets the values of the peripherals of all connected hummingbirds
     * and flutters to their default values.
     */
    private void stopAll() {
        for (Hummingbird hummingbird : connectedHummingbirds.values())
            hummingbird.stopAll();
        for (Flutter flutter : connectedFlutters.values())
            flutter.stopAll();
    }

}
