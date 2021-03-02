package com.birdbraintechnologies.birdblox.Robots;

import android.util.Log;

import com.birdbraintechnologies.birdblox.Bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.HBState;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.RobotStateObject;
import com.birdbraintechnologies.birdblox.Util.DeviceUtil;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;


/**
 * Represents a Hummingbird device and all of its functionality: Setting outputs, reading sensors
 *
 * @author Terence Sun (tsun1215)
 * @author Shreyan Bakshi (AppyFizz)
 * @author Zhendong Yuan (yzd1998111)
 */
//public class Hummingbird extends Robot<HBState> implements UARTConnection.RXDataListener {
public class Hummingbird extends Robot<HBState, HBState> {
    private final String TAG = this.getClass().getSimpleName();

    /*
     * Command prefixes for the Hummingbird according to spec
     * More info: http://www.hummingbirdkit.com/learning/hummingbird-duo-usb-protocol
     */
    private static final byte TRI_LED_CMD = 'O';
    private static final byte LED_CMD = 'L';
    private static final byte MOTOR_CMD = 'M';
    private static final byte VIB_MOTOR_CMD = 'V';
    private static final byte SERVO_CMD = 'S';
    private static final byte READ_SENSOR_CMD = 's';
    private static final byte READ_ALL_CMD = 'G';
    private static final byte STOP_PERIPH_CMD = 'X';
    private static final byte TERMINATE_CMD = 'R';
    private static final byte PING_CMD = 'z';
    private static final String RENAME_CMD = "AT+GAPDEVNAME";

    private static final byte[] TERMINATECOMMAND = new byte[]{(byte) 'R'};
    private static final byte[] STOPALLCOMMAND = new byte[]{(byte) 'X' };
    private static final byte[] STOPPOLLCOMMAND = new byte[]{READ_ALL_CMD, '6'};
    private static final byte[] STARTPOLLCOMMAND = new byte[]{READ_ALL_CMD, '5'};
    private static final byte[] FIRMWARECOMMAND = "G4".getBytes();

    private static final double HBIRD_BATTERY_INDEX = 4;
    private static final double HBIRD_BATTERY_CONSTANT = 0;
    private static final double HBIRD_RAW_TO_VOLTAGE = 0.0406;
    private static final double HBIRD_GREEN_THRESHOLD = 4.75;
    private static final double HBIRD_YELLOW_THRESHOLD = 4.63;

    private static final int SETALL_INTERVAL_IN_MILLIS = 32; //TODO: Should this be different or the same?


    // This represents the firmware version 2.2a
    private static final byte minFirmwareVersion1 = 2;
    private static final byte minFirmwareVersion2 = 2;
    private static final String minFirmwareVersion3 = "a";

    // This represents the firmware version 2.2b
    private static final byte latestFirmwareVersion1 = 2;
    private static final byte latestFirmwareVersion2 = 2;
    private static final String latestFirmwareVersion3 = "b";


    /**
     * Initializes a Hummingbird device
     *
     * @param conn Connection established with the Hummingbird device
     */
    public Hummingbird(final UARTConnection conn) {
        super(conn, RobotType.Hummingbird, true);

        oldPrimaryState = new HBState();
        newPrimaryState = new HBState();

        //These states aren't used in the Hummingbird case. Can they be removed somehow?
        oldSecondaryState = new HBState();
        newSecondaryState = new HBState();
    }

    /**
     * Sets the output of the given output type according to args
     *
     * @param outputType Type of the output
     * @param args       Arguments for setting the output
     * @return True if the output was successfully set, false otherwise
     */
    @Override
    public boolean setOutput(String outputType, Map<String, List<String>> args) {
        // Handle stop output type (since it doesn't have a port specification)
        if (outputType.equals("stop")) {
            return stopAll();
        }

        // All remaining outputs are of the format: /out/<outputType>/<port>/<args>...

        int port = Integer.parseInt(args.get("port").get(0));

        switch (outputType) {
            case "servo":
                return setRbSOOutput(oldPrimaryState.getServo(port), newPrimaryState.getServo(port), Integer.parseInt(args.get("angle").get(0)));
            case "motor":
                return setRbSOOutput(oldPrimaryState.getMotor(port), newPrimaryState.getMotor(port), Integer.parseInt(args.get("speed").get(0)));
            case "vibration":
                return setRbSOOutput(oldPrimaryState.getVibrator(port), newPrimaryState.getVibrator(port),
                        Integer.parseInt(args.get("intensity").get(0)));
            case "led":
                return setRbSOOutput(oldPrimaryState.getLED(port), newPrimaryState.getLED(port), Integer.parseInt(args.get("intensity").get(0)));
            case "triled":
                return setRbSOOutput(oldPrimaryState.getTriLED(port), newPrimaryState.getTriLED(port), Integer.parseInt(args.get("red").get(0)),
                        Integer.parseInt(args.get("green").get(0)), Integer.parseInt(args.get("blue").get(0)));
        }
        return false;
    }

    @Override
    protected void setOutputHelper(RobotStateObject newobj) {}

    /**
     * Reads the value of the sensor at the given port and returns the formatted value according to
     * sensorType
     *
     * @param sensorType Type of sensor connected to the port (dictates format of the returned
     *                   value)
     * @param portString Port that the sensor is connected to
     * @return A string representing the value of the sensor
     */
    @Override
    public String readSensor(String sensorType, String portString, String axisString) {
        byte rawSensorValue;
        synchronized (rawSensorValuesLock) {
            int port = Integer.parseInt(portString) - 1;
            rawSensorValue = rawSensorValues[port];
        }
        switch (sensorType) {
            case "distance":
                return Double.toString(DeviceUtil.RawToDist(rawSensorValue));
            case "temperature":
                return Double.toString(DeviceUtil.RawToTemp(rawSensorValue));
            case "sound":
                //The duo sound sensor returns raw values already in the range of 0 to 100.
                return Byte.toString(rawSensorValue);
            case "light":
            case "sensor":
            default:
                return Double.toString(DeviceUtil.RawToPercent(rawSensorValue));
        }
    }

    @Override
    protected void notifyIncompatible() {
        runJavascript("CallbackManager.robot.disconnectIncompatible('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode(getFirmwareVersion()) + "', '" + bbxEncode(getMinFirmwareVersion()) + "')");
    }

    @Override
    public byte[] getCalibrateCommand() {
        return null;
    }

    @Override
    public byte[] getResetEncodersCommand() {
        return null;
    }

    @Override
    public byte[] getStartPollCommand() {
        return STARTPOLLCOMMAND;
    }

    @Override
    public byte[] getStopPollCommand() {
        return STOPPOLLCOMMAND;
    }

    @Override
    public byte[] getTerminateCommand() {
        return TERMINATECOMMAND;
    }

    @Override
    public byte[] getStopAllCommand() {
        return STOPALLCOMMAND;
    }

    @Override
    public double[] getBatteryConstantsArray() {
        return new double[]{
                HBIRD_BATTERY_INDEX,
                HBIRD_BATTERY_CONSTANT,
                HBIRD_RAW_TO_VOLTAGE,
                HBIRD_GREEN_THRESHOLD,
                HBIRD_YELLOW_THRESHOLD};
    }

    @Override
    public int getCompassIndex() { //Hummingbird does not have a compass
        return 0;
    }

    @Override
    protected void sendSecondaryState(int delayInMillis) { } //Hummingbird does not actually have a secondary state.


    @Override
    public String getHardwareVersion() {
        if (cfresponse == null || cfresponse.length < 2) { return null; }
        return Byte.toString(cfresponse[0]) + Byte.toString(cfresponse[1]);
    }

    public String getFirmwareVersion() {
        try {
            return Byte.toString(cfresponse[2]) + "." + Byte.toString(cfresponse[3]) + new String(new byte[]{cfresponse[4]}, "utf-8");
        } catch (UnsupportedEncodingException | ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Hummingbird firmware version: " + e.getMessage());
            return null;
        }
    }

    String getMinFirmwareVersion() {
        return Byte.toString(minFirmwareVersion1) + "." + Byte.toString(minFirmwareVersion2) + minFirmwareVersion3;
    }

    public String getLatestFirmwareVersion() {
        return Byte.toString(latestFirmwareVersion1) + "." + Byte.toString(latestFirmwareVersion2) + latestFirmwareVersion3;
    }

    @Override
    public boolean hasMinFirmware() {
        try {
            int fw1 = (int) cfresponse[2];//g4response[2];
            int fw2 = (int) cfresponse[3];//g4response[3];
            String fw3 = new String(new byte[]{cfresponse[4]}, "utf-8");//new String(new byte[]{g4response[4]}, "utf-8");
            if (fw1 >= minFirmwareVersion1) {
                if (fw1 > minFirmwareVersion1) return true;
                if (fw2 >= minFirmwareVersion2) {
                    return ((fw2 > minFirmwareVersion2) || (fw3.compareTo(minFirmwareVersion3) >= 0));
                }
            }
            return false;
        } catch (UnsupportedEncodingException | ArrayIndexOutOfBoundsException | NullPointerException e) {
            Log.e(TAG, "Hummingbird firmware version: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean hasLatestFirmware() {
        try {
            int fw1 = (int) cfresponse[2];//g4response[2];
            int fw2 = (int) cfresponse[3];//g4response[3];
            String fw3 = new String(new byte[]{cfresponse[4]}, "utf-8");//new String(new byte[]{g4response[4]}, "utf-8");
            if (fw1 >= latestFirmwareVersion1) {
                if (fw1 > latestFirmwareVersion1) return true;
                if (fw2 >= latestFirmwareVersion2) {
                    return ((fw2 > latestFirmwareVersion2) || (fw3.compareTo(latestFirmwareVersion3) >= 0));
                }
            }
            return false;
        } catch (UnsupportedEncodingException | ArrayIndexOutOfBoundsException | NullPointerException e) {
            Log.e(TAG, "Hummingbird firmware version: " + e.getMessage());
            return false;
        }
    }

    @Override
    public byte[] getFirmwareCommand() {
        return FIRMWARECOMMAND;
    }

}