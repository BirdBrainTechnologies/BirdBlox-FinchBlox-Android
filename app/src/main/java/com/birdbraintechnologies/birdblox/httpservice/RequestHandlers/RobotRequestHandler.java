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
import android.widget.Toast;

import com.birdbraintechnologies.birdblox.Bluetooth.BluetoothHelper;
import com.birdbraintechnologies.birdblox.Bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblox.Bluetooth.UARTSettings;
import com.birdbraintechnologies.birdblox.Robots.Hummingbird;
import com.birdbraintechnologies.birdblox.Robots.Hummingbit;
import com.birdbraintechnologies.birdblox.Robots.Microbit;
import com.birdbraintechnologies.birdblox.Robots.Robot;
import com.birdbraintechnologies.birdblox.Robots.RobotType;
import com.birdbraintechnologies.birdblox.httpservice.HttpService;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fi.iki.elonen.NanoHTTPD;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.Robots.RobotType.robotTypeFromString;
import static fi.iki.elonen.NanoHTTPD.MIME_PLAINTEXT;

/**
 * @author AppyFizz (Shreyan Bakshi)
 * @author Zhendong Yuan (yzd1998111)
 */

public class RobotRequestHandler implements RequestHandler {
    private final String TAG = this.getClass().getSimpleName();

    private static final String FIRMWARE_UPDATE_URL = "http://www.hummingbirdkit.com/learning/installing-birdblox#BurnFirmware";
    /* UUIDs for different Hummingbird features */
    private static final String DEVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    private static final UUID HB_UART_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID HB_TX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID HB_RX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");

    // TODO: Remove this, it is the same across devices
    private static final UUID RX_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static HashSet<String> hummingbirdsToConnect = new HashSet<>();
    public static HashSet<String> hummingbitsToConnect = new HashSet<>();
    public static HashSet<String> microbitsToConnect = new HashSet<>();

    HttpService service;
    private static BluetoothHelper btHelper;
    private static HashMap<String, Thread> threadMap;

    private static UARTSettings HBUARTSettings;
    private static HashMap<String, Hummingbird> connectedHummingbirds;

    private static UARTSettings HBitUARTSettings;
    private static HashMap<String, Hummingbit> connectedHummingbits;

    private static UARTSettings MBitUARTSettings;
    private static HashMap<String, Microbit> connectedMicrobits;

    private AlertDialog.Builder builder;
    private AlertDialog robotInfoDialog;


    public static HashMap<String, BluetoothGatt> deviceGatt;

    public RobotRequestHandler(HttpService service) {
        this.service = service;
        btHelper = service.getBluetoothHelper();
        threadMap = new HashMap<>();

        connectedHummingbirds = new HashMap<>();
        connectedHummingbits = new HashMap<>();
        connectedMicrobits = new HashMap<>();
        deviceGatt = new HashMap<>();
        // Build Hummingbird UART settings
        HBUARTSettings = (new UARTSettings.Builder())
                .setUARTServiceUUID(HB_UART_UUID)
                .setRxCharacteristicUUID(HB_RX_UUID)
                .setTxCharacteristicUUID(HB_TX_UUID)
                .setRxConfigUUID(RX_CONFIG_UUID)
                .build();

        HBitUARTSettings = (new UARTSettings.Builder())
                .setUARTServiceUUID(HB_UART_UUID)
                .setRxCharacteristicUUID(HB_RX_UUID)
                .setTxCharacteristicUUID(HB_TX_UUID)
                .setRxConfigUUID(RX_CONFIG_UUID)
                .build();
        MBitUARTSettings = (new UARTSettings.Builder())
                .setUARTServiceUUID(HB_UART_UUID)
                .setRxCharacteristicUUID(HB_RX_UUID)
                .setTxCharacteristicUUID(HB_TX_UUID)
                .setRxConfigUUID(RX_CONFIG_UUID)
                .build();

    }


    @Override
    public NanoHTTPD.Response handleRequest(NanoHTTPD.IHTTPSession session, List<String> args) {
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
                responseBody = disconnectFromRobot(robotTypeFromString(m.get("type").get(0)), m.get("id").get(0));
                break;
            case "out":
                robot = getRobotFromId(robotTypeFromString(m.get("type").get(0)), m.get("id").get(0));
                if (robot == null) {
                    runJavascript("CallbackManager.robot.updateStatus('" + m.get("id").get(0) + "', false);");
                    return NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Robot " + m.get("id").get(0) + " was not found.");
                } else if (!robot.setOutput(path[1], m)) {
                    //TODO: Is it really true that when you fail to set output it always means not connected?
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
                    String sensorPort = null;
                    String sensorAxis = null;
                    if (m.get("port") != null) {
                        sensorPort = m.get("port").get(0);
                    }

                    if (m.get("axis") != null) {
                        sensorAxis = m.get("axis").get(0);
                    }
                    String sensorValue = robot.readSensor(m.get("sensor").get(0), sensorPort, sensorAxis);
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
     * Finds a robotId in the list of connected robots. Null if it does not exist.
     *
     * @param robotType The type of the robot to be found. Must be 'hummingbird' or 'hummingbit' or 'microbit'.
     * @param robotId   Robot ID to find.
     * @return The connected Robot if it exists, null otherwise.
     */
    private static Robot getRobotFromId(RobotType robotType, String robotId) {
        if (robotType == RobotType.Hummingbird) {
            return connectedHummingbirds.get(robotId);
        } else if (robotType == RobotType.Hummingbit) {
            return connectedHummingbits.get(robotId);
        } else {
            return connectedMicrobits.get(robotId);
        }
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
    public static String connectToRobot(RobotType robotType, String robotId) {
        if (robotType == RobotType.Hummingbird) {
            connectToHummingbird(robotId);
        } else if (robotType == RobotType.Hummingbit) {
            connectToHummingbit(robotId);
        } else {
            connectToMicrobit(robotId);
        }
        return "";
    }


    private static void connectToHummingbird(final String hummingbirdId) {
        if (connectedHummingbirds.containsKey(hummingbirdId) == false) {
            final UARTSettings HBUART = HBUARTSettings;
            if (hummingbirdsToConnect.contains(hummingbirdId)) {
                hummingbirdsToConnect.remove(hummingbirdId);
            }
            try {
                Thread hbConnectionThread = new Thread() {
                    @Override
                    public void run() {
                        UARTConnection hbConn = btHelper.connectToDeviceUART(hummingbirdId, HBUART);
                        if (hbConn != null && hbConn.isConnected() && connectedHummingbirds != null) {
                            Hummingbird hummingbird = new Hummingbird(hbConn);
                            connectedHummingbirds.put(hummingbirdId, hummingbird);
                            hummingbird.setConnected();
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
    }
    private static void connectToHummingbit(final String hummingbitId) {
        if (connectedHummingbits.containsKey(hummingbitId) == false) {
            final UARTSettings HBitUART = HBitUARTSettings;
            if (hummingbitsToConnect.contains(hummingbitId)) {
                hummingbitsToConnect.remove(hummingbitId);
            }
            try {
                Thread hbitConnectionThread = new Thread() {
                    @Override
                    public void run() {
                        UARTConnection hbitConn = btHelper.connectToDeviceUART(hummingbitId, HBitUART);
                        if (hbitConn != null && hbitConn.isConnected() && connectedHummingbits != null) {
                            Hummingbit hummingbit = new Hummingbit(hbitConn);
                            connectedHummingbits.put(hummingbitId, hummingbit);
                            hummingbit.setConnected();
                        }
                    }
                };
                hbitConnectionThread.start();
                final Thread oldThread = threadMap.put(hummingbitId, hbitConnectionThread);
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
                Log.e("ConnectHBit", " Error while connecting to HBit " + e.getMessage());
            }
        }
    }

    private static void connectToMicrobit(final String microbitId) {
        if (connectedMicrobits.containsKey(microbitId) == false) {
            final UARTSettings MBitUART = MBitUARTSettings;
            if (microbitsToConnect.contains(microbitId)) {
                microbitsToConnect.remove(microbitId);
            }
            try {
                Thread mbitConnectionThread = new Thread() {
                    @Override
                    public void run() {
                        UARTConnection mbitConn = btHelper.connectToDeviceUART(microbitId, MBitUART);
                        if (mbitConn != null && mbitConn.isConnected() && connectedMicrobits != null) {
                            Microbit microbit = new Microbit(mbitConn);
                            connectedMicrobits.put(microbitId, microbit);
                            microbit.setConnected();
                        }
                    }
                };
                mbitConnectionThread.start();
                final Thread oldThread = threadMap.put(microbitId, mbitConnectionThread);
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
                Log.e("ConnectHBit", " Error while connecting to HBit " + e.getMessage());
            }
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
        if (robotType == RobotType.Hummingbird) {
            disconnectFromHummingbird(robotId);
        } else if (robotType == RobotType.Hummingbit) {
            disconnectFromHummingbit(robotId);
        } else {
            disconnectFromMicrobit(robotId);
        }
        hummingbirdsToConnect = new HashSet<>();
        hummingbitsToConnect = new HashSet<>();
        microbitsToConnect = new HashSet<>();
        btHelper.stopScan();

        runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(robotId) + "', false);");

        Log.d("TotStat", "Connected Hummingbirds: " + connectedHummingbirds.toString());
        Log.d("TotStat", "Connected Hummingbits: " + connectedHummingbits.toString());
        Log.d("TotStat", "Connected Hummingbits: " + connectedMicrobits.toString());
        return robotType.toString() + " disconnected successfully.";
    }

    /**
     * @param hummingbirdId
     */
    public static void disconnectFromHummingbird(String hummingbirdId) {
        try {
            Hummingbird hummingbird = (Hummingbird) getRobotFromId(RobotType.Hummingbird, hummingbirdId);
            if (hummingbird != null) {
                hummingbird.disconnect();
                if (hummingbird.getDisconnected()) {
                    connectedHummingbirds.remove(hummingbirdId);
                }
                Log.d("TotStat", "Removing hummingbird: " + hummingbirdId);
            } else {
                BluetoothGatt curDeviceGatt = deviceGatt.get(hummingbirdId);
                if (curDeviceGatt != null) {
                    curDeviceGatt.disconnect();
                    curDeviceGatt.close();
                    curDeviceGatt = null;
                    if (deviceGatt.containsKey(hummingbirdId)) {
                        deviceGatt.remove(hummingbirdId);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ConnectHB", " Error while disconnecting from HB " + e.getMessage());
        }
    }

    /**
     * @param hummingbitId
     */
    public static void disconnectFromHummingbit(String hummingbitId) {
        try {
            Hummingbit hummingbit = (Hummingbit) getRobotFromId(RobotType.Hummingbit, hummingbitId);
            if (hummingbit != null) {
                hummingbit.disconnect();
                if (hummingbit.getDisconnected()) {
                    connectedHummingbits.remove(hummingbitId);
                }
                Log.d("TotStat", "Removing hummingbit: " + hummingbitId);
            } else {
                BluetoothGatt curDeviceGatt = deviceGatt.get(hummingbitId);
                if (curDeviceGatt != null) {
                    curDeviceGatt.disconnect();
                    curDeviceGatt.close();
                    curDeviceGatt = null;
                    if (deviceGatt.containsKey(hummingbitId)) {
                        deviceGatt.remove(hummingbitId);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ConnectHB", " Error while disconnecting from HB " + e.getMessage());
        }
    }

    /**
     * @param microbitId
     */
    public static void disconnectFromMicrobit(String microbitId) {
        try {
            Microbit microbit = (Microbit) getRobotFromId(RobotType.Microbit, microbitId);
            if (microbit != null) {
                microbit.disconnect();
                if (microbit.getDisconnected()) {
                    connectedMicrobits.remove(microbitId);
                }
                Log.d("TotStat", "Removing microbit: " + microbitId);
            } else {
                BluetoothGatt curDeviceGatt = deviceGatt.get(microbitId);
                if (curDeviceGatt != null) {
                    curDeviceGatt.disconnect();
                    curDeviceGatt.close();
                    curDeviceGatt = null;
                    if (deviceGatt.containsKey(microbitId)) {
                        deviceGatt.remove(microbitId);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("ConnectHB", " Error while disconnecting from MB " + e.getMessage());
        }
    }

    public static void disconnectAll() {
        hummingbirdsToConnect = null;
        hummingbitsToConnect = null;
        microbitsToConnect = null;
        if (connectedHummingbirds != null) {
            for (String individualHummingBird : connectedHummingbirds.keySet()) {
                disconnectFromHummingbird(individualHummingBird);
            }
        }

        if (connectedHummingbits != null) {
            for (String individualHummingBit : connectedHummingbits.keySet()) {
                disconnectFromHummingbit(individualHummingBit);
            }
        }

        if (connectedMicrobits != null) {
            for (String individualMicroBit : connectedMicrobits.keySet()) {
                disconnectFromMicrobit(individualMicroBit);
            }
        }

    }

    /**
     * @param robotType
     * @return
     */
    private String getTotalStatus(RobotType robotType) {
        if (robotType == RobotType.Hummingbird) {
            return getTotalHBStatus();
        } else if (robotType == RobotType.Hummingbit) {
            return getTotalHBitStatus();
        } else {
            return getTotalMBitStatus();
        }
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
    private String getTotalHBitStatus() {
        Log.d("TotStat", "Connected Hummingbits: " + connectedHummingbits.toString());
        if (connectedHummingbits.size() == 0) {
            return "2";  // No hummingbits connected
        }
        for (Hummingbit hummingbit : connectedHummingbits.values()) {
            if (!hummingbit.isConnected()) {
                return "0";  // Some hummingbit is disconnected
            }
        }
        return "1";  // All hummingbits are OK
    }

    /**
     * @return
     */
    private String getTotalMBitStatus() {
        Log.d("TotStat", "Connected Microbits: " + connectedMicrobits.toString());
        if (connectedMicrobits.size() == 0) {
            return "2";  // No hummingbits connected
        }
        for (Microbit microbit : connectedMicrobits.values()) {
            if (!microbit.isConnected()) {
                return "0";  // Some hummingbit is disconnected
            }
        }
        return "1";  // All hummingbits are OK
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
        Robot robot = getRobotFromId(robotType, robotId);
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
        for (Hummingbird hummingbird : connectedHummingbirds.values())
            hummingbird.stopAll();
        for (Hummingbit hummingbit : connectedHummingbits.values())
            hummingbit.stopAll();
        for (Microbit microbit : connectedMicrobits.values())
            microbit.stopAll();
    }

}