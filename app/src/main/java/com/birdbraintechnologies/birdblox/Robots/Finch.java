package com.birdbraintechnologies.birdblox.Robots;

import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import com.birdbraintechnologies.birdblox.Bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.FinchMotorState;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.FinchState;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.RobotStateObject;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.TriLED;
import com.birdbraintechnologies.birdblox.Util.DeviceUtil;
import com.birdbraintechnologies.birdblox.Util.NamingHandler;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.finchesToConnect;
import static io.reactivex.android.schedulers.AndroidSchedulers.from;


/**
 * Represents a Hummingbit device and all of its functionality: Setting outputs, reading sensors
 * @author Zhendong Yuan (yzd1998111)
 */
//public class Finch extends Robot<FinchState> implements UARTConnection.RXDataListener {
public class Finch extends Robot<FinchState, FinchMotorState> {

    //private final String TAG = this.getClass().getSimpleName();
    /*
     * Command prefixes for the Hummingbit according to spec
     */

    //private static final byte READ_ALL_CMD = 'b';
    //private static final byte TERMINATE_CMD = (byte) 0xDF;
    //private static final byte RESET_ENCODER_CMD = (byte) 0xD5;
    private static final int SYMBOL = 0;
    private static final int FLASH = 1;
    //private static final int SETALL_INTERVAL_IN_MILLIS = 48;//32;//64;//32;
    //private static final int COMMAND_TIMEOUT_IN_MILLIS = 5000;
    //private static final int SEND_ANYWAY_INTERVAL_IN_MILLIS = 50;
    //private static final int START_SENDING_INTERVAL_IN_MILLIS = 0;
    //private static final int MONITOR_CONNECTION_INTERVAL_IN_MILLIS = 1000;
    //private static final int MAX_NO_CF_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS = 15000;
    //private static final int MAX_NO_NORMAL_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS = 3000;

    private static final double FINCH_RAW_TO_VOLTAGE = 0.00937;
    private static final double FINCH_BATTERY_CONSTANT = 320;
    private static final double FINCH_GREEN_THRESHOLD = 3.51375;
    private static final double FINCH_YELLOW_THRESHOLD = 3.3732;
    private static final double FINCH_BATTERY_INDEX = 6;

    private static final int FINCH_COMPASS_INDEX = 16;

    private static final byte[] FIRMWARECOMMAND = new byte[]{(byte) 0xD4};
    private static final byte[] RESETENCODERSCOMMAND = new byte[]{(byte) 0xD5};
    private static final byte[] CALIBRATECOMMAND = new byte[]{(byte) 0xCE, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte[] STARTPOLLCOMMAND = new byte[]{(byte) 'b', (byte) 'g'};
    private static final byte[] STOPPOLLCOMMAND = new byte[]{(byte) 'b', (byte) 's'};
    private static final byte[] TERMINATECOMMAND = new byte[]{(byte) 0xDF};

    private static final byte latestMicroBitVersion = 0x01;
    private static final byte latestSMDVersion = 0x01;
    //private static int microBitVersion = 0;
    //private static int SMDVersion = 0;

    /*private AtomicBoolean cf;
    private AtomicLong cf_sent;
    private AtomicLong last_sent;
    private AtomicLong last_successfully_sent;*/


    //private UARTConnection conn;
    //private byte[] rawSensorValues;
    //private Object rawSensorValuesLock = new Object();

    //private final ReentrantLock lock;
    //private final Condition doneSending;

    //private final HandlerThread sendThread;
    //private final HandlerThread monitorThread;

    //private Disposable sendDisposable;
    //private Disposable monitorDisposable;

    //private byte[] cfresponse;
    //private FinchMotorState oldFMState = new FinchMotorState();
    //private FinchMotorState newFMState = new FinchMotorState();

    //private boolean ATTEMPTED = false;
    //private boolean DISCONNECTED = false;
    //private String last_battery_status;
    //private boolean isCalibratingCompass = false;

    /*private AtomicBoolean FORCESEND = new AtomicBoolean(false);
    private AtomicBoolean CALIBRATE = new AtomicBoolean(false);
    private AtomicBoolean RESETENCODERS = new AtomicBoolean(false);*/


    /**
     * Initializes a Finch device
     *
     * @param conn Connection established with the Hummingbit device
     */
    public Finch(final UARTConnection conn) {
        super(conn, RobotType.Finch);
        Log.d(TAG, "about to set up finch.");
        //this.conn = conn;

        oldPrimaryState = new FinchState();
        newPrimaryState = new FinchState();
        oldSecondaryState = new FinchMotorState();
        newSecondaryState = new FinchMotorState();

        /*FIRMWARECOMMAND[0] = (byte) 0xD4;
        CALIBRATECOMMAND[0] = (byte) 0xCE;
        CALIBRATECOMMAND[1] = (byte) 0xFF;
        CALIBRATECOMMAND[2] = (byte) 0xFF;
        CALIBRATECOMMAND[3] = (byte) 0xFF;
        RESETENCODERSCOMMAND[0] = (byte) 0xD5;*/

        /*cf = new AtomicBoolean(true);
        last_sent = new AtomicLong(System.currentTimeMillis());
        last_successfully_sent = new AtomicLong(System.currentTimeMillis());*/
        //last_battery_status = "";

        //lock = new ReentrantLock();
        //doneSending = lock.newCondition();

        /*sendThread = new HandlerThread("SendThread");
        if (!sendThread.isAlive())
            sendThread.start();
        from(sendThread.getLooper());
        Runnable sendRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    lock.tryLock(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
                    sendToRobot();
                    doneSending.signal();
                    if (DISCONNECTED) {
                        return;
                    }
                } catch (NullPointerException | InterruptedException | IllegalMonitorStateException e) {
                    Log.e("SENDHBSIG", "Signalling failed " + e.getMessage());
                } finally {
                    if (lock.isHeldByCurrentThread())
                        lock.unlock();
                }
            }
        };
        sendDisposable = from(sendThread.getLooper()).schedulePeriodicallyDirect(sendRunnable,
                START_SENDING_INTERVAL_IN_MILLIS, SETALL_INTERVAL_IN_MILLIS, TimeUnit.MILLISECONDS);

        monitorThread = new HandlerThread("SendThread");
        if (!monitorThread.isAlive())
            monitorThread.start();

        Runnable monitorRunnable = new Runnable() {
            @Override
            public void run() {
                final long timeOut = cf.get() ? MAX_NO_CF_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS : MAX_NO_NORMAL_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS;
                final long curSysTime = System.currentTimeMillis();
                final long prevTime = last_successfully_sent.get();
                final long passedTime = curSysTime - prevTime;
                if (passedTime >= timeOut) {
                    try {
                        runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', false);");
                        runJavascript("CallbackManager.robot.updateBatteryStatus('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode("3") + "');");
                        new Thread() {
                            @Override
                            public void run() {
                                super.run();
                                String macAddr = getMacAddress();
                                //RobotRequestHandler.disconnectFromHummingbit(macAddr);
                                RobotRequestHandler.disconnectFromRobot(RobotType.Finch, macAddr);
                                synchronized (finchesToConnect) {
                                    if (!finchesToConnect.contains(macAddr)) {
                                        finchesToConnect.add(macAddr);
                                    }
                                }
                                if (DISCONNECTED) {
                                    return;
                                }
                            }
                        }.start();
                    } catch (Exception e) {
                        Log.e(TAG, "Exception while auto-disconnecting: " + e.getMessage());
                    }
                }
            }
        };
        monitorDisposable = AndroidSchedulers.from(monitorThread.getLooper()).schedulePeriodicallyDirect(monitorRunnable,
                START_SENDING_INTERVAL_IN_MILLIS, MONITOR_CONNECTION_INTERVAL_IN_MILLIS, TimeUnit.MILLISECONDS);
*/
        Log.d(TAG, "Done setting up finch");
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
        return RESETENCODERSCOMMAND;
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
    public double[] getBatteryConstantsArray() {
        return new double[]{
                FINCH_BATTERY_INDEX,
                FINCH_BATTERY_CONSTANT,
                FINCH_RAW_TO_VOLTAGE,
                FINCH_GREEN_THRESHOLD,
                FINCH_YELLOW_THRESHOLD};
    }

    @Override
    public int getCompassIndex() {
        return FINCH_COMPASS_INDEX;
    }

    /**
     * Actually sends the commands to the physical Finch,
     * based on certain conditions.
     */
  /*  public synchronized void sendToRobot() {
        long currentTime = System.currentTimeMillis();
        boolean fmSent = false;
        if (cf.get()) {
            if (cf_sent == null) {
                // Send here
                setSendingTrue();
                conn.addRxDataListener(this);
                boolean success = conn.writeBytes(FIRMWARECOMMAND);
                Log.d(TAG, "write firmware bytes? " + success);
                if (success) {
                    cf_sent = new AtomicLong(currentTime);
                } else {
                    return;
                }
            }

            //while(cfresponse == null && currentTime+500 > System.currentTimeMillis() ) {
            //    Log.d(TAG, "Waiting for firmware version...");
            //}
            if (cfresponse != null && cfresponse.length > 0) {
                // Successfully sent CF command
                if (last_successfully_sent != null)
                    last_successfully_sent.set(currentTime);
                cf.set(false);
                runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
                try {
                    if (rawSensorValues == null) {
                        setSendingTrue();
                        startPollingSensors();
                    }
                } catch (RuntimeException e) {
                    Log.e(TAG, "Error getting FN sensor values: " + e.getMessage());
                }

                //if (!hasLatestFirmware()) {
                //    cf.set(true);
                //    runJavascript("CallbackManager.robot.disconnectIncompatible('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode(getMicroBitVersion()) + "', '" + bbxEncode(getLatestMicroBitVersion()) + "', '" + bbxEncode(getSMDVersion()) + "', '" + bbxEncode(getLatestSMDVersion()) + "')");
                //    disconnect();
                //}

            } else if (cf_sent.get() + 2000 > currentTime) {
                Log.d(TAG, "waiting for a cf response...");
            } else {
                Log.e(TAG, "Timed out waiting for firmware version.");
                cf.set(true);
                runJavascript("CallbackManager.robot.disconnectIncompatible('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode(getMicroBitVersion()) + "', '" + bbxEncode(getLatestMicroBitVersion()) + "', '" + bbxEncode(getSMDVersion()) + "', '" + bbxEncode(getLatestSMDVersion()) + "')");
                disconnect();
            }
            setSendingFalse();
            last_sent.set(currentTime);
            return;
        }
        // Not CF
        if (isCurrentlySending()) {
            Log.d(TAG,"Currently sending...");
            // do nothing in this case
            return;
        }
        if (CALIBRATE.get()) {
            CALIBRATE.set(false);
            setSendingTrue();
            if (conn.writeBytes(CALIBRATECOMMAND)) {
                // Successfully sent Non-CF command
                if (last_successfully_sent != null)
                    last_successfully_sent.set(currentTime);
                runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
                SystemClock.sleep(200);
                isCalibratingCompass = true;
            } else {
                // Sending Non-CF command failed
            }
            setSendingFalse();
            last_sent.set(currentTime);
            return;
        }
        if (RESETENCODERS.get()) {
            RESETENCODERS.set(false);
            setSendingTrue();
            if (conn.writeBytes(RESETENCODERSCOMMAND)) {
                // Successfully sent Non-CF command
                if (last_successfully_sent != null)
                    last_successfully_sent.set(currentTime);
                runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
                SystemClock.sleep(200);
            } else {
                // Sending Non-CF command failed
            }
            setSendingFalse();
            last_sent.set(currentTime);
            return;
        }

        //if (!setAllPending && (!newFMState.equals(oldFMState) || FORCESEND.get())) {
        if (!newFMState.equals(oldFMState) || FORCESEND.get()) {
            setSendingTrue();

            //Determine what needs to be sent
            if (!newFMState.getMotors().equals(oldFMState.getMotors())) {
                newFMState.setSendMotors(true);
            }
            if (!newFMState.getLedArray().equals(oldFMState.getLedArray())) {
                newFMState.setSendLEDArray(true);
            }
            Log.d(TAG, "writing new fm state");
            if (conn.writeBytes(newFMState.setAll())) {
                // Successfully sent Non-CF command
                if (last_successfully_sent != null)
                    last_successfully_sent.set(currentTime);
                if (newFMState.getSendMotors()) {
                    newFMState.resetMotors();
                    newFMState.setSendMotors(false);
                }
                newFMState.setSendLEDArray(false);
                oldFMState.copy(newFMState);
                runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
            } else {
                // Sending Non-CF command failed
                Log.e(TAG, "Sending Non-CF (newFMState) command failed");
            }

            setSendingFalse();
            last_sent.set(currentTime);
            FORCESEND.set(false);
            fmSent = true;

            //if (!statesEqual()) { setAllPending = true; }

            // Not currently sending s
            //} else if (!statesEqual()) {
        }
        if (!statesEqual()) {
            // Not currently sending, but oldState and newState are different
            // Send here
            if (fmSent) {
                final byte[] pendingCommand = newState.setAll();
                oldState.copy(newState);
                Log.d(TAG, "Just set set all states equal. Will send command soon...");
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        long currentTime = System.currentTimeMillis();
                        setSendingTrue();
                        Log.d(TAG, "writing delayed new setall state");
                        if (conn.writeBytes(pendingCommand)) {
                            // Successfully sent Non-CF command
                            if (last_successfully_sent != null)
                                last_successfully_sent.set(currentTime);
                            runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
                        } else {
                            // Sending Non-CF command failed
                            Log.e(TAG, "Sending Non-CF command failed");
                        }
                        setSendingFalse();
                        last_sent.set(currentTime);
                        Log.d(TAG, "done writing delayed new setall state");
                    }
                }, SETALL_INTERVAL_IN_MILLIS/2);
            } else {
                setSendingTrue();
                Log.d(TAG, "writing new setall state");
                if (conn.writeBytes(newState.setAll())) {
                    // Successfully sent Non-CF command
                    if (last_successfully_sent != null)
                        last_successfully_sent.set(currentTime);
                    oldState.copy(newState);

                    runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
                } else {
                    // Sending Non-CF command failed
                    Log.e(TAG, "Sending Non-CF command failed");
                }
                setSendingFalse();
                last_sent.set(currentTime);
            }
            //setAllPending = false;
        } else {
            // Not currently sending, and oldState and newState are the same
            if (currentTime - last_sent.get() >= SEND_ANYWAY_INTERVAL_IN_MILLIS) {
                setSendingTrue();
                if (conn.writeBytes(newState.setAll())) {
                    // Successfully sent Non-G4 command
                    if (last_successfully_sent != null)
                        last_successfully_sent.set(currentTime);
                    oldState.copy(newState);
                    runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
                } else {
                    // Sending Non-G4 command failed
                }
                setSendingFalse();
                last_sent.set(currentTime);
            }

            //setAllPending = false; //??
        }
    }*/

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
            case "beak":
                return setRbSOOutput(oldPrimaryState.getTriLED(1), newPrimaryState.getTriLED(1), Integer.parseInt(args.get("red").get(0)),
                        Integer.parseInt(args.get("green").get(0)), Integer.parseInt(args.get("blue").get(0)));
            case "tail":
                String portText = args.get("port").get(0);
                int red = Integer.parseInt(args.get("red").get(0));
                int green = Integer.parseInt(args.get("green").get(0));
                int blue = Integer.parseInt(args.get("blue").get(0));
                if (portText.equals("all")) {
                    boolean success = true;
                    for (int i = 2; i < 6; i++) {
                        if (!setRbSOOutput(oldPrimaryState.getTriLED(i), newPrimaryState.getTriLED(i), red, green, blue)) {
                            success = false;
                        }
                    }
                    return success;
                } else {
                    port = Integer.parseInt(portText) + 1;
                    return setRbSOOutput(oldPrimaryState.getTriLED(port), newPrimaryState.getTriLED(port), red, green, blue);
                }
            case "buzzer":
                int duration = Integer.parseInt(args.get("duration").get(0));
                int note = Integer.parseInt(args.get("note").get(0));
                if (duration != 0 && note != 0) {
                    return setRbSOOutput(oldPrimaryState.getBuzzer(), newPrimaryState.getBuzzer(), note, duration);
                } else {
                    return true;
                }
            case "motors":
                int speedL = Integer.parseInt(args.get("speedL").get(0));
                int ticksL = Integer.parseInt(args.get("ticksL").get(0));
                int speedR = Integer.parseInt(args.get("speedR").get(0));
                int ticksR = Integer.parseInt(args.get("ticksR").get(0));
                Log.d(TAG, "set motors " + speedL + ", " + ticksL + ", " + speedR + ", " + ticksR);
                return setRbSOOutput(oldSecondaryState.getMotors(), newSecondaryState.getMotors(), speedL, ticksL, speedR, ticksR);
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
                int[] ints = new int[chars.length + 1];
                for (int i = 0; i < chars.length; i++) {
                    ints[i] = (int) chars[i];
                    if (ints[i] > 255) {
                        ints[i] = 254;
                    }
                }
                ints[ints.length - 1] = FLASH;
                return setRbSOOutput(oldSecondaryState.getLedArray(), newSecondaryState.getLedArray(), ints);
            case "compassCalibrate":
                CALIBRATE.set(true);
                return true;
            case "resetEncoders":
                RESETENCODERS.set(true);
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
     * @param axisString axis or position requested
     * @return A string representing the value of the sensor
     */
    @Override
    public String readSensor(String sensorType, String portString, String axisString) {

        int[] rawDistance = new int[2];
        byte[] rawLight = new byte[2];
        boolean isMoving = false;
        byte[] rawLine = new byte[2];
        byte rawBattery;
        byte[] rawEncoder = new byte[6];
        byte[] rawAccelerometerValue = new byte[3];
        byte rawButtonShakeValue;
        byte[] rawMagnetometerValue = new byte[3];

        synchronized (rawSensorValuesLock) {

            rawDistance[0] = rawSensorValues[0] & 0xFF;
            rawDistance[1] = rawSensorValues[1] & 0xFF;
            rawLight[0] = rawSensorValues[2];
            rawLight[1] = rawSensorValues[3];
            isMoving = (rawSensorValues[4] < 0);
            rawLine[0] = rawSensorValues[4];
            rawLine[1] = rawSensorValues[5];
            rawBattery = rawSensorValues[6];
            rawEncoder[0] = rawSensorValues[7];
            rawEncoder[1] = rawSensorValues[8];
            rawEncoder[2] = rawSensorValues[9];
            rawEncoder[3] = rawSensorValues[10];
            rawEncoder[4] = rawSensorValues[11];
            rawEncoder[5] = rawSensorValues[12];
            rawAccelerometerValue[0] = rawSensorValues[13];
            rawAccelerometerValue[1] = rawSensorValues[14];
            rawAccelerometerValue[2] = rawSensorValues[15];
            rawButtonShakeValue = rawSensorValues[16];
            rawMagnetometerValue[0] = rawSensorValues[17];
            rawMagnetometerValue[1] = rawSensorValues[18];
            rawMagnetometerValue[2] = rawSensorValues[19];

        }
        //Log.d(TAG, "read sensor raw values: " + Arrays.toString(rawSensorValues));
        switch (sensorType) {
            case "isMoving":
                return (isMoving ? "1" : "0");
            case "distance":
                int num = (rawDistance[0] << 8) + rawDistance[1];
                //int scaled = (int)Math.round((double)num * (117/100));
                //Log.d(TAG, "returning distance " + Arrays.toString(rawDistance) + "; " + num + "; " + scaled);
                //return Integer.toString(scaled);
                return Integer.toString(num);
            case "light":
                //compensate for the effect of the beak light on the light sensors
                TriLED currentBeak = oldPrimaryState.getTriLED(1);
                long R = Math.round((currentBeak.getRed() & 0xFF) / 2.55); //using 0-100 rgb values
                long G = Math.round((currentBeak.getGreen() & 0xFF) / 2.55);
                long B = Math.round((currentBeak.getBlue() & 0xFF) / 2.55);
                Double raw;
                Double correction;
                if (axisString.equals("right")) {
                    raw = Double.valueOf(rawLight[1] & 0xFF);
                    correction = 6.40473070e-03*R +  1.41015162e-02*G +  5.05547817e-02*B +  3.98301391e-04*R*G +  4.41091223e-04*R*B +  6.40756862e-04*G*B + -4.76971242e-06*R*G*B;
                } else {
                    raw = Double.valueOf(rawLight[0] & 0xFF);
                    correction = 1.06871493e-02*R +  1.94526614e-02*G +  6.12409825e-02*B +  4.01343475e-04*R*G + 4.25761981e-04*R*B +  6.46091068e-04*G*B + -4.41056971e-06*R*G*B;
                }
                Log.d(TAG, "Correcting " + axisString + " light sensor raw value " + raw + " by " + Math.round(correction) + " : " + R + "," + G + "," + B);
                return Integer.toString(Math.min(100, Math.max(0, (int)Math.round(raw - correction))));
            case "line":
                if (axisString.equals("right")) {
                    return Integer.toString(rawLine[1]);
                } else {
                    int val = rawLine[0] & 0xFF;
                    if (val > 127) { val -= 128; } //To remove movement flag
                    return Integer.toString(val);
                }
            case "battery":
                return Double.toString(((double)(rawBattery & 0xFF) + FINCH_BATTERY_CONSTANT) * FINCH_RAW_TO_VOLTAGE);
            case "encoder":
                int i = 0;
                if (axisString.equals("right")) { i = 3; }

                int msb = rawEncoder[i] & 0xFF;
                int ssb = rawEncoder[i+1] & 0xFF;
                int lsb = rawEncoder[i+2] & 0xFF;

                int unsigned = (msb << 16) + (ssb << 8) + lsb;
                int signed = (unsigned << 8) >> 8;

                return Integer.toString(signed);
            case "magnetometer":
                return Double.toString(DeviceUtil.RawToFinchMag(rawMagnetometerValue, axisString));
            case "accelerometer":
                return Double.toString(DeviceUtil.RawToFinchAccl(rawAccelerometerValue, axisString) * 196.0 / 1280.0);
            case "compass":
                double heading = DeviceUtil.RawToCompass(rawAccelerometerValue, rawMagnetometerValue, true);
                //turn it around so that the finch beak points north at 0
                return Double.toString((heading + 180) % 360);
            case "buttonA":
                return (((rawButtonShakeValue >> 4) & 0x1) == 0x0) ? "1" : "0";
            case "buttonB":
                return (((rawButtonShakeValue >> 5) & 0x1) == 0x0) ? "1" : "0";
            case "shake":
                return ((rawButtonShakeValue & 0x1) == 0x0) ? "0" : "1";
            case "screenUp":
                return DeviceUtil.RawToFinchAccl(rawAccelerometerValue, "z") < -51 ? "1" : "0";
            case "screenDown":
                return DeviceUtil.RawToFinchAccl(rawAccelerometerValue, "z") > 51 ? "1" : "0";
            case "tiltLeft":
                return DeviceUtil.RawToFinchAccl(rawAccelerometerValue, "x") > 51 ? "1" : "0";
            case "tiltRight":
                return DeviceUtil.RawToFinchAccl(rawAccelerometerValue, "x") < -51 ? "1" : "0";
            case "logoUp":
                return DeviceUtil.RawToFinchAccl(rawAccelerometerValue, "y") < -51 ? "1" : "0";
            case "logoDown":
                return DeviceUtil.RawToFinchAccl(rawAccelerometerValue, "y") > 51 ? "1" : "0";
            default:
                return "";
        }
    }

    @Override
    protected void sendSecondaryState(int delayInMillis) {
        //Determine what needs to be sent
        if (!newSecondaryState.getMotors().equals(oldSecondaryState.getMotors())) {
            newSecondaryState.setSendMotors(true);
        }
        if (!newSecondaryState.getLedArray().equals(oldSecondaryState.getLedArray())) {
            newSecondaryState.setSendLEDArray(true);
        }
        byte[] cmd = newSecondaryState.setAll();
        //Log.d(TAG, "writing new fm state");
        if (sendCommand(newSecondaryState.setAll())) {
            if (newSecondaryState.getSendMotors()) {
                newSecondaryState.resetMotors();
                newSecondaryState.setSendMotors(false);
            }
            newSecondaryState.setSendLEDArray(false);
            oldSecondaryState.copy(newSecondaryState);
        }
        //sendInFuture(cmd, delayInMillis);
    }

    /*private void startPollingSensors() { conn.writeBytes(new byte[]{READ_ALL_CMD, 'g'}); }

    private void stopPollingSensors() {
        conn.writeBytes(new byte[]{READ_ALL_CMD, 's'});
    }*/

    /*private boolean setRbSOOutput(RobotStateObject oldobj, RobotStateObject newobj, int... values) {
        try {
            lock.tryLock(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
            AtomicInteger count = new AtomicInteger(0);
            while (!newobj.equals(oldobj)) {
                Log.d(TAG, "waiting for equal states...");
                if (count.incrementAndGet() > 1) {
                    Log.e(TAG, "Max wait exceeded!");
                    break;
                }
                doneSending.await(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
            }
            if (newobj.equals(oldobj)) {
                newobj.setValue(values);
                if (lock.isHeldByCurrentThread()) {
                    //doneSending.signal();
                    lock.unlock();
                }
                return true;
            }
        } catch (InterruptedException | IllegalMonitorStateException | IllegalStateException | IllegalThreadStateException e) {
            Log.e(TAG, "Error setting finch output: " + e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread())
                lock.unlock();
        }
        return false;
    }*/

    /**
     * Resets all finch peripherals to their default values.
     *
     * @return True if succeeded in changing state, false otherwise
     */
    /*public boolean stopAll() {
        try {
            lock.tryLock(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
            while (!statesEqual()) {
                doneSending.await(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
            }
            if (statesEqual()) {
                conn.writeBytes(new byte[]{TERMINATE_CMD});
                newState.resetAll();
                newFMState.resetAll();
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
    }*/

    /**
     * Returns whether or not this device is connected
     *
     * @return True if connected, false otherwise
     */
    /*public boolean isConnected() {
        return conn.isConnected();
    }*/

    /*public void setConnected() {
        DISCONNECTED = false;
    }*/

    /**
     * Disconnects the device
     */
    /*public void disconnect() {
        if (!DISCONNECTED) {
            if (ATTEMPTED) {
                forceDisconnect();
                return;
            }
            ATTEMPTED = true;
            conn.writeBytes(new byte[]{TERMINATE_CMD});
            newFMState.resetAll();

            AndroidSchedulers.from(sendThread.getLooper()).shutdown();
            sendThread.getLooper().quit();
            if (sendDisposable != null && !sendDisposable.isDisposed())
                sendDisposable.dispose();
            sendThread.interrupt();
            sendThread.quit();

            AndroidSchedulers.from(monitorThread.getLooper()).shutdown();
            monitorThread.getLooper().quit();
            if (monitorDisposable != null && !monitorDisposable.isDisposed())
                monitorDisposable.dispose();
            monitorThread.interrupt();
            monitorThread.quit();

            if (conn != null) {
                conn.removeRxDataListener(this);
                stopPollingSensors();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
                conn.disconnect();
            }
            ATTEMPTED = false;
            DISCONNECTED = true;
        }
    }*/

    /*public boolean getDisconnected() {
        return DISCONNECTED;
    }*/

    /*public void forceDisconnect() {
        String macAddr = getMacAddress();
        if (!DISCONNECTED) {
            ATTEMPTED = false;
            AndroidSchedulers.from(sendThread.getLooper()).shutdown();
            sendThread.getLooper().quit();
            if (sendDisposable != null && !sendDisposable.isDisposed())
                sendDisposable.dispose();
            sendThread.interrupt();
            sendThread.quit();

            AndroidSchedulers.from(monitorThread.getLooper()).shutdown();
            monitorThread.getLooper().quit();
            if (monitorDisposable != null && !monitorDisposable.isDisposed())
                monitorDisposable.dispose();
            monitorThread.interrupt();
            monitorThread.quit();

            if (conn != null) {
                conn.removeRxDataListener(this);
                conn.forceDisconnect();
            }
            synchronized (finchesToConnect) {
                if (!finchesToConnect.contains(macAddr)) {
                    finchesToConnect.add(macAddr);
                }
            }
            DISCONNECTED = true;
        }
    }*/

    /*@Override
    public void onRXData(byte[] newData) {
        synchronized (rawSensorValuesLock) {
            if (cf.get()){
                this.cfresponse = newData;
                return;
            }
            this.rawSensorValues = newData;
            String curBatteryStatus = "";
            double batteryVoltage = ((newData[6] & 0xFF) + FINCH_BATTERY_CONSTANT) * FINCH_RAW_TO_VOLTAGE;
            if (batteryVoltage > FINCH_GREEN_THRESHOLD) {
                curBatteryStatus = "2";
            } else if (batteryVoltage > FINCH_YELLOW_THRESHOLD) {
                curBatteryStatus = "1";
            } else {
                curBatteryStatus = "0";
            }
            if (!curBatteryStatus.equals(last_battery_status)) {
                last_battery_status = curBatteryStatus;
                runJavascript("CallbackManager.robot.updateBatteryStatus('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode(curBatteryStatus) + "');");
            }
            if (isCalibratingCompass) {
                boolean success = ((newData[16] >> 2) & 0x1) == 0x1;
                boolean failure = ((newData[16] >> 3) & 0x1) == 0x1;
                if (success){
                    Log.v(TAG, "Calibration success!");
                    isCalibratingCompass = false;
                    runJavascript("CallbackManager.robot.compassCalibrationResult('" + getMacAddress() + "', 'true');");
                } else if (failure){
                    Log.v(TAG, "Calibration failure");
                    isCalibratingCompass = false;
                    runJavascript("CallbackManager.robot.compassCalibrationResult('" + getMacAddress() + "', 'false');");
                } else {
                    Log.v(TAG, "Calibration unknown");
                }
            }
        }
    }*/

    /*public String getMacAddress() {
        try {
            return conn.getBLEDevice().getAddress();
        } catch (NullPointerException e) {
            Log.e(TAG, "Error getting finch mac address: " + e.getMessage());
            return null;
        }
    }*/

    /*public String getName() {
        try {
            return NamingHandler.GenerateName(mainWebViewContext, conn.getBLEDevice().getAddress());
        } catch (NullPointerException e) {
            Log.e(TAG, "Error getting finch name: " + e.getMessage());
            return null;
        }
    }*/

    /*public String getGAPName() {
        try {
            return conn.getBLEDevice().getName();
        } catch (NullPointerException e) {
            Log.e(TAG, "Error getting finch gap name: " + e.getMessage());
            return null;
        }
    }*/

    @Override
    protected void notifyIncompatible() {
        runJavascript("CallbackManager.robot.disconnectIncompatible('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode(getMicroBitVersion()) + "', '" + bbxEncode(getLatestMicroBitVersion()) + "', '" + bbxEncode(getSMDVersion()) + "', '" + bbxEncode(getLatestSMDVersion()) + "')");
    }

    private String getCFResponse(int index) {
        try {
            return Byte.toString(cfresponse[index]);
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Finch cf version error: " + e.getMessage());
            return "";
        }
    }

    @Override
    public String getHardwareVersion() {
        return getCFResponse(0);
    }

    public String getLatestMicroBitVersion() {
        return Byte.toString(latestMicroBitVersion);
    }

    public String getLatestSMDVersion() {
        return Byte.toString(latestSMDVersion);
    }

    public String getMicroBitVersion() {
        return getCFResponse(1);
    }

    public String getSMDVersion() {
        return getCFResponse(2);
    }

    @Override
    public boolean hasLatestFirmware() {
        return true;
        /*try {
            int microBitVersion = (int) cfresponse[1];
            int SMDVersion = (int) cfresponse[2];
            if (microBitVersion >= (int) latestMicroBitVersion && SMDVersion >= (int) latestSMDVersion) {
                return true;
            } else {
                return false;
            }
        } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            Log.e(TAG, "Finch firmware version error: " + e.getMessage());
            return false;
        }*/
    }

    @Override
    public boolean hasMinFirmware() {
        return true;
    }
}