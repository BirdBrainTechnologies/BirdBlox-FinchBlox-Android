package com.birdbraintechnologies.birdblox.Robots;

import android.util.Log;

import com.birdbraintechnologies.birdblox.Bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.HBitState;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.LedArrayState;
import com.birdbraintechnologies.birdblox.Util.DeviceUtil;

import java.util.List;
import java.util.Map;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;


/**
 * Represents a Hummingbird Bit device
 */
public class Hummingbit extends Robot<HBitState, LedArrayState> {
    private final String TAG = this.getClass().getSimpleName();

    private static final int SYMBOL = 0;
    private static final int FLASH = 1;
    private static final int ROTATION = 1;
    private static final int POSITION = 0;
    private static final byte latestMicroBitVersion = 0x01;
    private static final byte latestSMDVersion = 0x01;
    private static int microBitVersion = 0;
    private static int SMDVersion = 0;

    private static final double HBIT_BATTERY_INDEX = 3;
    private static final double HBIT_BATTERY_CONSTANT = 0;
    private static final double HBIT_RAW_TO_VOLTAGE = 0.0406;
    private static final double HBIT_GREEN_THRESHOLD = 4.75;
    private static final double HBIT_YELLOW_THRESHOLD = 4.4;

    private static final int HUMMINGBIT_COMPASS_INDEX = 7;

    private static final byte[] FIRMWARECOMMAND = new byte[]{(byte) 0xCF};
    private static final byte[] CALIBRATECOMMAND = new byte[]{(byte) 0xCE, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte[] STARTPOLLCOMMAND = new byte[]{(byte) 'b', (byte) 'g'};
    private static final byte[] V2STARTPOLLCOMMAND = new byte[]{(byte) 'b', (byte) 'p'};
    private static final byte[] STOPPOLLCOMMAND = new byte[]{(byte) 'b', (byte) 's'};
    private static final byte[] TERMINATECOMMAND = new byte[]{(byte) 0xCB};


    /**
     * Initializes a Hummingbit device
     *
     * @param conn Connection established with the Hummingbit device
     */
    public Hummingbit(final UARTConnection conn) {
        super(conn, RobotType.Hummingbit, false);

        oldPrimaryState = new HBitState();
        newPrimaryState = new HBitState();
        oldSecondaryState = new LedArrayState();
        newSecondaryState = new LedArrayState();

    }

    @Override
    public byte[] getFirmwareCommand() {
        return FIRMWARECOMMAND;
    }

    @Override
    public byte[] getCalibrateCommand() {
        return CALIBRATECOMMAND;
    }

    @Override
    public byte[] getResetEncodersCommand() {
        return null;
    }

    @Override
    public byte[] getStartPollCommand() {
        if (hasV2Microbit()) {
            return V2STARTPOLLCOMMAND;
        } else {
            return STARTPOLLCOMMAND;
        }
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
        return getTerminateCommand();
    }

    @Override
    public double[] getBatteryConstantsArray() {
        return new double[]{
                HBIT_BATTERY_INDEX,
                HBIT_BATTERY_CONSTANT,
                HBIT_RAW_TO_VOLTAGE,
                HBIT_GREEN_THRESHOLD,
                HBIT_YELLOW_THRESHOLD};
    }

    @Override
    public int getCompassIndex() {
        return HUMMINGBIT_COMPASS_INDEX;
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
        int port = 0;
        switch (outputType) {
            case "servo":
                port = Integer.parseInt(args.get("port").get(0));
                if (args.get("angle") == null) {
                    return setRbSOOutput(oldPrimaryState.getHBitServo(port), newPrimaryState.getHBitServo(port), Integer.parseInt(args.get("percent").get(0)), ROTATION);
                } else {
                    return setRbSOOutput(oldPrimaryState.getHBitServo(port), newPrimaryState.getHBitServo(port), Integer.parseInt(args.get("angle").get(0)), POSITION);
                }
            case "led":
                port = Integer.parseInt(args.get("port").get(0));
                return setRbSOOutput(oldPrimaryState.getLED(port), newPrimaryState.getLED(port), Integer.parseInt(args.get("intensity").get(0)));
            case "triled":
                port = Integer.parseInt(args.get("port").get(0));
                return setRbSOOutput(oldPrimaryState.getTriLED(port), newPrimaryState.getTriLED(port), Integer.parseInt(args.get("red").get(0)),
                        Integer.parseInt(args.get("green").get(0)), Integer.parseInt(args.get("blue").get(0)));
            case "buzzer":
                int duration = Integer.parseInt(args.get("duration").get(0));
                int note = Integer.parseInt(args.get("note").get(0));
                if (duration != 0 && note != 0) {
                    return setRbSOOutput(oldPrimaryState.getHBBuzzer(port), newPrimaryState.getHBBuzzer(port), note, duration);
                } else {
                    return true;
                }
            case "ledArray":
                String charactersInInts = args.get("ledArrayStatus").get(0);
                int[] bitsInInt = new int[charactersInInts.length() + 1];
                for (int i = 0; i < charactersInInts.length(); i++) {
                    bitsInInt[i] = Integer.parseInt(charactersInInts.charAt(i) + "");
                }
                bitsInInt[bitsInInt.length - 1] = SYMBOL;
                return setRbSOOutput(oldSecondaryState.getLedArray(), newSecondaryState.getLedArray(), bitsInInt);
            case "printBlock":
                FORCESEND.set(true);
                String printString = args.get("printString").get(0);
                char[] chars = printString.toCharArray();
                int [] ints = new int[chars.length + 1];
                for (int i = 0; i < chars.length; i++){
                    ints[i] = (int)chars[i];
                    if (ints[i] > 255) {
                        ints[i] = 254;
                    }
                }
                ints[ints.length-1] = FLASH;
                return setRbSOOutput(oldSecondaryState.getLedArray(), newSecondaryState.getLedArray(), ints);
            case "compassCalibrate":
                CALIBRATE.set(true);
                return true;
        }
        return false;
    }

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
        boolean V2 = hasV2Microbit();
        int rawSensorValue = 0;
        byte[] rawMagnetometerValue = new byte[6];
        byte[] rawAccelerometerValue = new byte[3];
        byte[] rawButtonShakeValue = new byte[1];
        byte rawSound = 0;
        byte rawTemp = 0;

        synchronized (rawSensorValuesLock) {
            if (portString != null) {
                int port = Integer.parseInt(portString) - 1;
                rawSensorValue = (rawSensorValues[port] & 0xFF);
            } else {
                rawAccelerometerValue[0] = rawSensorValues[4];
                rawAccelerometerValue[1] = rawSensorValues[5];
                rawAccelerometerValue[2] = rawSensorValues[6];
                rawButtonShakeValue[0] = rawSensorValues[7];
                rawMagnetometerValue[0] = rawSensorValues[8];
                rawMagnetometerValue[1] = rawSensorValues[9];
                rawMagnetometerValue[2] = rawSensorValues[10];
                rawMagnetometerValue[3] = rawSensorValues[11];
                rawMagnetometerValue[4] = rawSensorValues[12];
                rawMagnetometerValue[5] = rawSensorValues[13];
                if (V2) {
                    rawSound = rawSensorValues[14];
                    rawTemp = rawSensorValues[15];
                }
            }
        }

        switch (sensorType) {
            case "distance":
                return Double.toString(DeviceUtil.RawToDistance(rawSensorValue));
            case "sound":
                return Double.toString(DeviceUtil.RawToSound(rawSensorValue));
            case "light":
                return Double.toString(DeviceUtil.RawToLight(rawSensorValue));
            case "magnetometer":
                return Double.toString(DeviceUtil.RawToMag(rawMagnetometerValue, axisString));
            case "accelerometer":
                return Double.toString(DeviceUtil.RawToAccl(rawAccelerometerValue, axisString));
            case "compass":
                return Double.toString(DeviceUtil.RawToCompass(rawAccelerometerValue, rawMagnetometerValue, false));
            case "buttonA":
                return (((rawButtonShakeValue[0] >> 4) & 0x1) == 0x0) ? "1" : "0";
            case "buttonB":
                return (((rawButtonShakeValue[0] >> 5) & 0x1) == 0x0) ? "1" : "0";
            case "shake":
                return ((rawButtonShakeValue[0] & 0x1) == 0x0) ? "0" : "1";
            case "screenUp":
                return rawAccelerometerValue[2] < -51 ? "1" : "0";
            case "screenDown":
                return rawAccelerometerValue[2] > 51 ? "1" : "0";
            case "tiltLeft":
                return rawAccelerometerValue[0] > 51 ? "1" : "0";
            case "tiltRight":
                return rawAccelerometerValue[0] < -51 ? "1" : "0";
            case "logoUp":
                return rawAccelerometerValue[1] < -51 ? "1" : "0";
            case "logoDown":
                return rawAccelerometerValue[1] > 51 ? "1" : "0";
            case "dial":
                return Double.toString(DeviceUtil.RawToKnob(rawSensorValue));
            case "V2sound":
                return Integer.toString(rawSound & 0xFF);
            case "V2temperature":
                return Integer.toString(rawTemp & 0xFF);
            case "V2touch":
                return (((rawButtonShakeValue[0] >> 1) & 0x1) == 0x0) ? "1" : "0";
            default:
                return Double.toString(DeviceUtil.RawToVoltage(rawSensorValue));
        }
    }

    @Override
    protected void sendSecondaryState(int delayInMillis) {
        byte[] cmd = newSecondaryState.setAll();
        if (sendCommand(newSecondaryState.setAll())) {
            oldSecondaryState.copy(newSecondaryState);
        }
        //sendInFuture(cmd, delayInMillis);
    }

    @Override
    protected void notifyIncompatible() {
        runJavascript("CallbackManager.robot.disconnectIncompatible('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode(getMicroBitVersion()) + "', '" + bbxEncode(getLatestMicroBitVersion()) + "', '" + bbxEncode(getSMDVersion()) + "', '" + bbxEncode(getLatestSMDVersion()) + "')");
    }

    @Override
    public String getHardwareVersion() {
        try {
            return Byte.toString(cfresponse[0]);
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Hummingbit hardware version: " + e.getMessage());
            return null;
        }
    }

    public String getLatestMicroBitVersion() {
        return Byte.toString(latestMicroBitVersion);
    }

    public String getLatestSMDVersion() {
        return Byte.toString(latestSMDVersion);
    }

    public String getMicroBitVersion() {
        return Integer.toString(microBitVersion);
    }

    public String getSMDVersion() {
        return Integer.toString(SMDVersion);
    }

    @Override
    public boolean hasLatestFirmware() {
        return true;
        /*try {
            microBitVersion = (int) cfresponse[1];
            SMDVersion = (int) cfresponse[2];
            if (microBitVersion >= (int) latestMicroBitVersion && SMDVersion >= (int) latestSMDVersion) {
                return true;
            } else {
                return false;
            }
        } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            Log.e(TAG, "Hummingbit firmware version: " + e.getMessage());
            return false;
        }*/
    }

    @Override
    public boolean hasMinFirmware() {
        return true;
    }
}

