package com.birdbraintechnologies.birdblox.httpservice.RequestHandlers;

import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.ScanFilter;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import com.birdbraintechnologies.birdblox.Bluetooth.BluetoothHelper;
import com.birdbraintechnologies.birdblox.Bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblox.Bluetooth.UARTSettings;
import com.birdbraintechnologies.birdblox.Robots.Finch;
import com.birdbraintechnologies.birdblox.Robots.Hummingbird;
import com.birdbraintechnologies.birdblox.Robots.Hummingbit;
import com.birdbraintechnologies.birdblox.Robots.Microbit;
import com.birdbraintechnologies.birdblox.Robots.Robot;
import com.birdbraintechnologies.birdblox.Robots.RobotType;
import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidResponse;
import com.birdbraintechnologies.birdblox.httpservice.NativeAndroidSession;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandler;
import com.birdbraintechnologies.birdblox.httpservice.Status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.Robots.RobotType.robotTypeFromString;


/**
 * @author AppyFizz (Shreyan Bakshi)
 * @author Zhendong Yuan (yzd1998111)
 */

public class RobotRequestHandler implements RequestHandler {
    private static final String TAG = RobotRequestHandler.class.getSimpleName();

    private static final String FIRMWARE_UPDATE_URL = "http://www.hummingbirdkit.com/learning/installing-birdblox#BurnFirmware";
    /* UUIDs for different Hummingbird features */
    private static final String DEVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final UUID UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    // TODO: Remove this, it is the same across devices... but actually all these uuids are the same across devices
    private static final UUID RX_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static HashSet<String> robotsToConnect = new HashSet<>(); //List of robots to autoreconnect by GAP name (So that they do not autoreconnect if the type has changed)

    //HttpService service;
    private static BluetoothHelper btHelper;
    private static HashMap<String, Thread> threadMap;

    private static UARTSettings uartSettings;
    private static HashMap<String, Robot> connectedRobots;

    private AlertDialog.Builder builder;
    private AlertDialog robotInfoDialog;


    public static HashMap<String, BluetoothGatt> deviceGatt;

    //public RobotRequestHandler(HttpService service) {
    public RobotRequestHandler(BluetoothHelper btService) {
        //this.service = service;
        //btHelper = service.getBluetoothHelper();
        btHelper = btService;
        threadMap = new HashMap<>();

        connectedRobots = new HashMap<>();

        deviceGatt = new HashMap<>();
        // Build Robot UART settings
        uartSettings = (new UARTSettings.Builder())
                        .setUARTServiceUUID(UART_UUID)
                        .setRxCharacteristicUUID(RX_UUID)
                        .setTxCharacteristicUUID(TX_UUID)
                        .setRxConfigUUID(RX_CONFIG_UUID)
                        .build();
    }


    @Override
    //public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
    public NativeAndroidResponse handleRequest(NativeAndroidSession session, List<String> args) {
        String[] path = args.get(0).split("/");
        Map<String, List<String>> m = session.getParameters();
        // Generate response body
        String responseBody = "";
        Robot robot;
        switch (path[0]) {
            case "startDiscover":
                responseBody = startScan();
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
                //responseBody = disconnectFromRobot(robotTypeFromString(m.get("type").get(0)), m.get("id").get(0));
                responseBody = disconnectFromRobot(m.get("id").get(0));
                break;
            case "out":
                //Log.d(TAG, "setting output: " + path[1]);
                //robot = getRobotFromId(robotTypeFromString(m.get("type").get(0)), m.get("id").get(0));
                robot = connectedRobots.get(m.get("id").get(0));
                if (robot == null) {
                    runJavascript("CallbackManager.robot.updateStatus('" + m.get("id").get(0) + "', false);");
                    //return NanoHTTPD.newFixedLengthResponse(
                    //        NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Robot " + m.get("id").get(0) + " was not found.");
                    return new NativeAndroidResponse(Status.NOT_FOUND, "Robot " + m.get("id").get(0) + " was not found.");
                } else if (!robot.setOutput(path[1], m)) {
                    //TODO: Is it really true that when you fail to set output it always means not connected?
                    runJavascript("CallbackManager.robot.updateStatus('" + m.get("id").get(0) + "', false);");
                    //return NanoHTTPD.newFixedLengthResponse(
                    //        NanoHTTPD.Response.Status.EXPECTATION_FAILED, MIME_PLAINTEXT, "Failed to send to robot " + m.get("id").get(0) + ".");
                    Log.e(TAG, "set output failed " + path[1]);
                    return new NativeAndroidResponse(Status.EXPECTATION_FAILED, "Failed to send to robot " + m.get("id").get(0) + ".");
                } else {
                    runJavascript("CallbackManager.robot.updateStatus('" + m.get("id").get(0) + "', true);");
                    responseBody = "Sent to robot " + m.get("type").get(0) + " successfully.";
                }
                //Log.d(TAG, "successfully set output: " + path[1]);
                break;
            case "in":
                //robot = getRobotFromId(robotTypeFromString(m.get("type").get(0)), m.get("id").get(0));
                robot = connectedRobots.get(m.get("id").get(0));
                if (robot == null) {
                    runJavascript("CallbackManager.robot.updateStatus('" + m.get("id").get(0) + "', false);");
                    //return NanoHTTPD.newFixedLengthResponse(
                    //        NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Robot " + m.get("id").get(0) + " was not found.");
                    return new NativeAndroidResponse(Status.NOT_FOUND, "Robot " + m.get("id").get(0) + " was not found.");
                } else {
                    String sensorPort = null;
                    String sensorAxis = null;
                    if (m.get("port") != null) {
                        sensorPort = m.get("port").get(0);
                    }
                    if (m.get("axis") != null) {
                        sensorAxis = m.get("axis").get(0);
                    }
                    if (m.get("position") != null){
                        sensorAxis = m.get("position").get(0);
                    }

                    String sensorValue = robot.readSensor(m.get("sensor").get(0), sensorPort, sensorAxis);
                    if (sensorValue == null) {
                        runJavascript("CallbackManager.robot.updateStatus('" + m.get("id").get(0) + "', false);");
                        //return NanoHTTPD.newFixedLengthResponse(
                        //        NanoHTTPD.Response.Status.NO_CONTENT, MIME_PLAINTEXT, "Failed to read sensors from robot " + m.get("id").get(0) + ".");
                        return new NativeAndroidResponse(Status.NO_CONTENT, "Failed to read sensors from robot " + m.get("id").get(0) + ".");
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

        //return NanoHTTPD.newFixedLengthResponse(
        //        NanoHTTPD.Response.Status.OK, MIME_PLAINTEXT, responseBody);
        return new NativeAndroidResponse(Status.OK, responseBody);
    }

    // TODO: Synchronization of below functions

    private static String startScan() {
        final List deviceFilter = generateDeviceFilter();

        if (BluetoothHelper.currentlyScanning) {
            return "";
        }
        if (BluetoothHelper.currentlyScanning) {
            stopDiscover();
        }
        new Thread() {
            @Override
            public void run() {
                btHelper.scanDevices(deviceFilter);
            }
        }.start();
        return "";
    }

    /**
     * Creates a Bluetooth scan Robot filter that only matches the required 'type' of Robot.
     *
     * @return List of scan filters.
     */
    private static List<ScanFilter> generateDeviceFilter() {
        String ROBOT_UUID = DEVICE_UUID;
        ScanFilter scanFilter = (new ScanFilter.Builder())
                .setServiceUuid(ParcelUuid.fromString(ROBOT_UUID))
                .build();
        List<ScanFilter> robotFilters = new ArrayList<>();
        robotFilters.add(scanFilter);
        return robotFilters;
    }

    /**
     * @param robotType
     * @param robotId
     * @return
     */
    public static String connectToRobot(final RobotType robotType, final String robotId) {
        Log.d(TAG, "connectToRobot " + robotType.toString() + " " + robotId);

        if (connectedRobots.containsKey(robotId)) {
            Log.e(TAG, "Connect request for robot that is already connected: " + robotId);
            return "";
        }

        //if (toConnect.contains(robotId)) { toConnect.remove(robotId); }

        try {
            Thread connectionThread = new Thread() {
                @Override
                public void run() {
                    UARTConnection conn = btHelper.connectToDeviceUART(robotId, uartSettings);
                    if (conn != null && conn.isConnected() && connectedRobots != null) {
                        String gapName = conn.getBLEDevice().getName();
                        if (robotsToConnect.contains(gapName)) { robotsToConnect.remove(gapName); }

                        Robot robot;
                        switch (robotType) {
                            case Hummingbird:
                                robot = new Hummingbird(conn);
                                break;
                            case Hummingbit:
                                robot = new Hummingbit(conn);
                                break;
                            case Microbit:
                                robot = new Microbit(conn);
                                break;
                            case Finch:
                                robot = new Finch(conn);
                                break;
                            default:
                                robot = null;
                        }
                        connectedRobots.put(robotId, robot);
                        robot.setConnected();
                        Log.d(TAG, "connectToRobot connected set.");
                    } else {
                        Log.e(TAG, "Error connecting to robot");
                    }
                }
            };
            connectionThread.start();
            final Thread oldThread = threadMap.put(robotId, connectionThread);
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
            Log.e(TAG, "Error connecting to robot " + robotId + ": " + e.getMessage());
        }

        return "";
    }

    /**
     * @param robotId
     * @return
     */
    public static String disconnectFromRobot(final String robotId) {
        new Thread() {
            @Override
            public void run() {
                super.run();
                Thread connThread = threadMap.get(robotId);
                if (connThread != null) connThread.interrupt();
            }
        }.start();

        try {
            Robot robot = connectedRobots.get(robotId);
            if (robot != null) {
                robotsToConnect.remove(robot.getGAPName());
                robot.disconnect();
                if (robot.getDisconnected()) {
                    connectedRobots.remove(robotId);
                }
                Log.d("TotStat", "Removing: " + robotId);
            } else {
                BluetoothGatt curDeviceGatt = deviceGatt.get(robotId);
                if (curDeviceGatt != null) {
                    curDeviceGatt.disconnect();
                    curDeviceGatt.close();
                    curDeviceGatt = null;
                    deviceGatt.remove(robotId);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error while disconnecting " + robotId + ": " + e.getMessage());
        }

        //robotsToConnect = new HashSet<>(); //TODO: Do we really want to reset this here?

        btHelper.stopScan();

        runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(robotId) + "', false);");
        Log.d("TotStat", "Connected Robots: " + connectedRobots.toString());

        return robotId + " disconnected successfully.";
    }

    public static void disconnectAll() {
        robotsToConnect = null;
        if (connectedRobots != null) {
            for (String individualRobot: connectedRobots.keySet()) {
                String s = disconnectFromRobot(individualRobot);
            }
        }
    }

    /**
     * @param robotType
     * @return
     */
    private String getTotalStatus(RobotType robotType) {
        Log.d("TotStat", "Robots connected: " + connectedRobots.toString());

        if (connectedRobots.size() == 0) {
            return "2";  // None connected
        }

        boolean robotsFoundForType = false;
        for (Robot robot : connectedRobots.values()){
            if (robot.type == robotType) {
                robotsFoundForType = true;
                if (!robot.isConnected()) {
                    return "0"; //Some robot of robotType is disconnected
                }
            }
        }
        return robotsFoundForType ? "1" : "2"; //All robots of robotType are OK
    }

    private static String stopDiscover() {
        if (btHelper != null)
            btHelper.stopScan();
        runJavascript("CallbackManager.robot.stopDiscover();");
        return "Bluetooth discovery stopped.";
    }

    private String showRobotInfo(RobotType robotType, String robotId) {
        builder = new AlertDialog.Builder(mainWebViewContext);

        // Get details
        //Robot robot = getRobotFromId(robotType, robotId);
        Robot robot = connectedRobots.get(robotId);
        if (robot == null) {
            return "Failed to show robot info. Robot not found.";
        }
        String name = robot.getName();
        String macAddress = robot.getMacAddress();
        String gapName = robot.getGAPName();
        String hardwareVersion = "";
        String firmwareVersion = "";
        if (robotType == RobotType.Hummingbird) {
            hardwareVersion = ((Hummingbird) robot).getHardwareVersion();
            firmwareVersion = ((Hummingbird) robot).getFirmwareVersion();
        } else if (robotType == RobotType.Hummingbit) {
            hardwareVersion = ((Hummingbit) robot).getHardwareVersion();
            firmwareVersion = "microBit: " + ((Hummingbit) robot).getMicroBitVersion() + "SMD: " + ((Hummingbit) robot).getSMDVersion();
        } else if (robotType == RobotType.Microbit) {
            hardwareVersion = ((Microbit) robot).getHardwareVersion();
            firmwareVersion = "microBit: " + ((Microbit) robot).getMicroBitVersion();
        }


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
     * and microbits and hummingbits to their default values.
     */
    private void stopAll() {
        for (Robot robot : connectedRobots.values())
            robot.stopAll();
    }

}