package com.birdbraintechnologies.birdblox.Robots;

import android.os.SystemClock;
import android.util.Log;

import com.birdbraintechnologies.birdblox.Bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.LedArrayState;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.MBState;
import com.birdbraintechnologies.birdblox.Util.DeviceUtil;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;


/**
 * Represents a stand alone micro:bit device
 */
public class Microbit extends Robot<MBState, LedArrayState> {
    private final String TAG = this.getClass().getSimpleName();

    private static final int SYMBOL = 0;
    private static final int FLASH = 1;
    private static final byte latestHardwareVersion = 0x01;
    private static final byte latestMicroBitVersion = 0x01;
    private static int microBitVersion = 0;

    private static final int MICROBIT_COMPASS_INDEX = 7;

    private static final byte[] FIRMWARECOMMAND = new byte[]{(byte) 0xCF};
    private static final byte[] CALIBRATECOMMAND = new byte[]{(byte) 0xCE, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte[] STARTPOLLCOMMAND = new byte[]{(byte) 'b', (byte) 'g'};
    private static final byte[] STOPPOLLCOMMAND = new byte[]{(byte) 'b', (byte) 's'};
    private static final byte[] TERMINATECOMMAND = new byte[]{(byte) 0xCB};


    /**
     * Initializes a Microbit device
     *
     * @param conn Connection established with the Microbit device
     */
    public Microbit(final UARTConnection conn) {
        super(conn, false);

        oldPrimaryState = new MBState();
        newPrimaryState = new MBState();
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
        return getTerminateCommand();
    }

    @Override
    public double[] getBatteryConstantsArray() { return null; }

    @Override
    public int getCompassIndex() {
        return MICROBIT_COMPASS_INDEX;
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
            case "write":
                port = Integer.parseInt(args.get("port").get(0));
                return setRbSOOutput(oldPrimaryState.getPad(port), newPrimaryState.getPad(port), Integer.parseInt(args.get("percent").get(0)));
            case "buzzer":
                int duration = Integer.parseInt(args.get("duration").get(0));
                int note = Integer.parseInt(args.get("note").get(0));
                if (duration != 0 && note != 0) {
                    return setRbSOOutput(oldPrimaryState.getHBBuzzer(), newPrimaryState.getHBBuzzer(), note, duration);
                } else {
                    return true;
                }
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
     * @param axisString axis or position requested
     * @return A string representing the value of the sensor
     */
    @Override
    public String readSensor(String sensorType, String portString, String axisString) {
        byte[] rawMagnetometerValue = new byte[6];
        byte[] rawAccelerometerValue = new byte[3];
        byte[] rawButtonShakeValue = new byte[1];
        byte[] rawPadValue = new byte[3];
        synchronized (rawSensorValuesLock) {
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
            rawPadValue[0] = rawSensorValues[0];
            rawPadValue[1] = rawSensorValues[1];
            rawPadValue[2] = rawSensorValues[2];
        }
        switch (sensorType) {
            case "magnetometer":
                return Double.toString(DeviceUtil.RawToMag(rawMagnetometerValue, axisString));
            case "accelerometer":
                return Double.toString(DeviceUtil.RawToAccl(rawAccelerometerValue, axisString));
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
            case "compass":
                return Double.toString(DeviceUtil.RawToCompass(rawAccelerometerValue, rawMagnetometerValue, false));
            case "pin":
                int padNum = Integer.parseInt(portString) - 1;
                //Check to make sure that pad is in read mode.
                if (!checkReadMode(padNum)){
                    //if not, set to read mode, wait for readings to start, and then read a new value.
                    if (setReadMode(padNum)){
                        SystemClock.sleep(200);
                        byte raw;
                        synchronized (rawSensorValuesLock) {
                            raw = rawSensorValues[padNum];
                        }
                        return DeviceUtil.RawToPad(raw);
                    } else {
                        return "";
                    }
                } else {
                    return DeviceUtil.RawToPad(rawPadValue[padNum]);
                }
            default:
                return "";
        }
    }
    private boolean checkReadMode(int pad){
        return (oldPrimaryState.mode == newPrimaryState.mode && newPrimaryState.mode[2*(pad+1)] == false &&
                newPrimaryState.mode[2*(pad+1)+1] == true);
    }
    private boolean setReadMode(int pad){
        try {
            lock.tryLock(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
            AtomicInteger count = new AtomicInteger(0);
            while (!newPrimaryState.mode.equals(oldPrimaryState.mode)) {
                if (count.incrementAndGet() > 1) break;
                doneSending.await(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
            }
            if (newPrimaryState.mode.equals(oldPrimaryState.mode)) {
                newPrimaryState.mode[(pad+1)*2] = false;
                newPrimaryState.mode[(pad+1)*2+1] = true;

                if (lock.isHeldByCurrentThread()) {
                    doneSending.signal();
                    lock.unlock();
                }
                return true;
            }
        } catch (InterruptedException | IllegalMonitorStateException | IllegalStateException | IllegalThreadStateException e) {
        } finally {
            if (lock.isHeldByCurrentThread())
                lock.unlock();
        }
        return false;
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
        runJavascript("CallbackManager.robot.disconnectIncompatible('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode(getMicroBitVersion()) + "', '" + bbxEncode(getLatestMicroBitVersion()) + "')");
    }

    @Override
    public String getHardwareVersion() {
        try {
            return Byte.toString(cfresponse[0]);
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Microbit hardware version: " + e.getMessage());
            return null;
        }
    }

    public String getLatestMicroBitVersion() {
        return Byte.toString(latestMicroBitVersion);
    }

    public String getMicroBitVersion() {
        return Integer.toString(microBitVersion);
    }

    @Override
    public boolean hasLatestFirmware() {
        return true;
        /*try {
            microBitVersion = (int) cfresponse[1];
            if (microBitVersion >= (int) latestMicroBitVersion) {
                return true;
            } else {
                return false;
            }
        } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            Log.e(TAG, "Microbit firmware version: " + e.getMessage());
            return false;
        }*/
    }

    @Override
    public boolean hasMinFirmware() {
        return true;
    }

    /*@Override
    protected void addToReconnect() {
        addToHashSet(microbitsToConnect);
    }*/
}

