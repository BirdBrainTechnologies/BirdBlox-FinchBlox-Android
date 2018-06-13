package com.birdbraintechnologies.birdblox.Robots;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.Toast;

import com.birdbraintechnologies.birdblox.Bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.HBitState;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.MBState;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.RobotStateObject;
import com.birdbraintechnologies.birdblox.Util.DeviceUtil;
import com.birdbraintechnologies.birdblox.Util.NamingHandler;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

import static android.content.ContentValues.TAG;
import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.hummingbitsToConnect;
import static io.reactivex.android.schedulers.AndroidSchedulers.from;

public class Hummingbit extends Robot<HBitState> implements UARTConnection.RXDataListener {
    /*
     * Command prefixes for the Hummingbit according to spec
     */

    private static final byte READ_ALL_CMD = 'b';
    private static final byte STOP_PERIPH_CMD = 'X';
    private static final byte TERMINATE_CMD = (byte) 0xCB;
    private static final int SYMBOL = 0;
    private static final int FLASH = 1;

    private static final int SETALL_INTERVAL_IN_MILLIS = 32;
    private static final int COMMAND_TIMEOUT_IN_MILLIS = 5000;
    private static final int SEND_ANYWAY_INTERVAL_IN_MILLIS = 50;
    private static final int START_SENDING_INTERVAL_IN_MILLIS = 0;
    private static final int MONITOR_CONNECTION_INTERVAL_IN_MILLIS = 1000;
    private static final int MAX_NO_CF_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS = 5000;
    private static final int MAX_NO_NORMAL_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS = 5000;
    private static final int ROTATION = 1;
    private static final int POSITION = 0;
    private static byte[] FIRMWARECOMMAND = new byte[1];
    private static final byte latestHardwareVersion = 0x01;
    private static final byte latestMicroBitVersion = 0x01;
    private static final byte latestSMDVersion = 0x01;
    private static int microBitVersion = 0;
    private static int SMDVersion = 0;

    private AtomicBoolean cf;
    private AtomicLong last_sent;
    private AtomicLong last_successfully_sent;

    private UARTConnection conn;
    private byte[] rawSensorValues;
    private Object rawSensorValuesLock = new Object();

    private final ReentrantLock lock;
    private final Condition doneSending;

    private final HandlerThread sendThread;
    private final HandlerThread monitorThread;

    private Disposable sendDisposable;
    private Disposable monitorDisposable;

    private byte[] cfresponse;
    private static MBState oldMBState = new MBState();
    private static MBState newMBState = new MBState();

    private static boolean ATTEMPTED = false;
    private static boolean DISCONNECTED = false;
    /**
     * Initializes a Hummingbit device
     *
     * @param conn Connection established with the Hummingbit device
     */
    public Hummingbit(final UARTConnection conn) {
        super();
        this.conn = conn;

        oldState = new HBitState();
        newState = new HBitState();


        FIRMWARECOMMAND[0] = (byte) 0xCF;

        cf = new AtomicBoolean(true);
        last_sent = new AtomicLong(System.currentTimeMillis());
        last_successfully_sent = new AtomicLong(System.currentTimeMillis());

        lock = new ReentrantLock();
        doneSending = lock.newCondition();

        sendThread = new HandlerThread("SendThread");
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
                if (last_successfully_sent == null) {
                    last_successfully_sent = new AtomicLong(System.currentTimeMillis());
                }
                long timeOut = cf.get() ? MAX_NO_CF_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS : MAX_NO_NORMAL_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS;
                long passedTime = System.currentTimeMillis() - last_successfully_sent.get();
                if (passedTime >= timeOut) {
                    try {
                        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                String HBitName = NamingHandler.GenerateName(mainWebViewContext, getMacAddress());
                                Toast.makeText(mainWebViewContext, "Connection to Hummingbit " + HBitName + " timed out.", Toast.LENGTH_SHORT).show();
                            }
                        });
                        synchronized (hummingbitsToConnect) {
                            hummingbitsToConnect.add(getMacAddress());
                        }
                        runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', false);");
                        new Thread() {
                            @Override
                            public void run() {
                                super.run();
                                disconnect();
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
    }


    /**
     * Actually sends the commands to the physical Hummingbit,
     * based on certain conditions.
     */
    public synchronized void sendToRobot() {
        long currentTime = System.currentTimeMillis();
        if (cf.get()) {
            // Send here
            setSendingTrue();

            cfresponse = conn.writeBytesWithResponse(FIRMWARECOMMAND);
            if (cfresponse != null && cfresponse.length > 0) {
                // Successfully sent CF command
                if (last_successfully_sent != null)
                    last_successfully_sent.set(currentTime);
                cf.set(false);
                runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
                if (!hasLatestFirmware()) {
                    cf.set(true);
                    runJavascript("CallbackManager.robot.disconnectIncompatible('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode(getMicroBitVersion()) + "', '" + bbxEncode(getLatestMicroBitVersion()) + "', '" + bbxEncode(getSMDVersion()) + "', '" + bbxEncode(getLatestSMDVersion()) + "')");
                    disconnect();
                }
            } else {
                // Sending Non-CF command failed
            }
            setSendingFalse();
            last_sent.set(currentTime);
            return;
        }
        // Not CF
        if (isCurrentlySending()) {
            // do nothing in this case
            return;
        }
        // Not currently sending s
        if (!statesEqual()) {
            // Not currently sending, but oldState and newState are different
            // Send here
            setSendingTrue();
            if (!newMBState.equals(oldMBState)) {

                if (conn.writeBytes(newState.setAll()) && conn.writeBytes(newMBState.setAll())) {
                    // Successfully sent Non-CF command
                    if (last_successfully_sent != null)
                        last_successfully_sent.set(currentTime);
                    oldState.copy(newState);
                    oldMBState.copy(newMBState);
                    runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
                } else {
                    // Sending Non-CF command failed
                }
            } else {
                if (conn.writeBytes(newState.setAll())) {
                    // Successfully sent Non-CF command
                    if (last_successfully_sent != null)
                        last_successfully_sent.set(currentTime);
                    oldState.copy(newState);
                    oldMBState.copy(newMBState);
                    runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
                } else {
                    // Sending Non-CF command failed
                }
            }

            setSendingFalse();
            last_sent.set(currentTime);
        } else {
            // Not currently sending, and oldState and newState are the same
            if (currentTime - last_sent.get() >= SEND_ANYWAY_INTERVAL_IN_MILLIS) {
                // Send here
                setSendingTrue();
                // Not currently sending, and oldState and newState are the same
                if (!newMBState.equals(oldMBState)) {
                    if (conn.writeBytes(newState.setAll()) && conn.writeBytes(newMBState.setAll())) {
                        // Successfully sent Non-CF command
                        if (last_successfully_sent != null)
                            last_successfully_sent.set(currentTime);
                        oldState.copy(newState);
                        oldMBState.copy(newMBState);
                        runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
                    } else {
                        // Sending Non-CF command failed
                    }
                } else {
                    if (conn.writeBytes(newState.setAll())) {
                        // Successfully sent Non-CF command
                        if (last_successfully_sent != null)
                            last_successfully_sent.set(currentTime);
                        oldState.copy(newState);
                        oldMBState.copy(newMBState);
                        runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
                    } else {
                        // Sending Non-CF command failed
                    }
                }
                setSendingFalse();
                last_sent.set(currentTime);
            }
        }
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
                    return setRbSOOutput(oldState.getHBitServo(port), newState.getHBitServo(port), Integer.parseInt(args.get("percent").get(0)), ROTATION);
                } else {
                    return setRbSOOutput(oldState.getHBitServo(port), newState.getHBitServo(port), Integer.parseInt(args.get("angle").get(0)), POSITION);
                }
            case "led":
                port = Integer.parseInt(args.get("port").get(0));
                return setRbSOOutput(oldState.getLED(port), newState.getLED(port), Integer.parseInt(args.get("intensity").get(0)));
            case "triled":
                port = Integer.parseInt(args.get("port").get(0));
                return setRbSOOutput(oldState.getTriLED(port), newState.getTriLED(port), Integer.parseInt(args.get("red").get(0)),
                        Integer.parseInt(args.get("green").get(0)), Integer.parseInt(args.get("blue").get(0)));
            case "buzzer":
                if (Integer.parseInt(args.get("duration").get(0)) != 0 && Integer.parseInt(args.get("note").get(0)) != 0) {
                    return setRbSOOutput(oldState.getHBBuzzer(port), newState.getHBBuzzer(port), Integer.parseInt(args.get("note").get(0)), Integer.parseInt(args.get("duration").get(0)));
                }
            case "ledArray":
                String charactersInInts = args.get("ledArrayStatus").get(0);
                int[] bitsInInt = new int[charactersInInts.length() + 1];
                for (int i = 0; i < charactersInInts.length(); i++) {
                    bitsInInt[i] = Integer.parseInt(charactersInInts.charAt(i) + "");
                }
                bitsInInt[bitsInInt.length - 1] = SYMBOL;
                return setRbSOOutput(oldMBState.getLedArray(), newMBState.getLedArray(), bitsInInt);
            case "printBlock":
                String printString = args.get("printString").get(0);
                printString = printString.replaceAll("[^a-zA-Z]", "");
                printString = printString.toUpperCase();
                byte[] tmpAscii = printString.getBytes(StandardCharsets.US_ASCII);
                int[] charsInInts = new int[tmpAscii.length + 1];

                for (int i = 0; i < tmpAscii.length; i++) {
                    charsInInts[i] = (int) tmpAscii[i];
                }
                charsInInts[charsInInts.length - 1] = FLASH;
                return setRbSOOutput(oldMBState.getLedArray(), newMBState.getLedArray(), charsInInts);
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
    public String readSensor(String sensorType, String portString, String axisString) {
        int rawSensorValue = 0;
        byte[] rawMagnetometerValue = new byte[6];
        byte[] rawAccelerometerValue = new byte[3];
        byte[] rawButtonShakeValue = new byte[1];
        byte[] rawBatteryValue = new byte[1];
        synchronized (rawSensorValuesLock) {
            try {
                if (rawSensorValues == null) {
                    rawSensorValues = startPollingSensors();
                    conn.addRxDataListener(this);
                }
                rawBatteryValue[0] = rawSensorValues[3];
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
                }
            } catch (RuntimeException e) {
                Log.e(TAG, "Error getting HB sensor values: " + e.getMessage());
                return null;
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
                return Double.toString(DeviceUtil.RawToCompass(rawAccelerometerValue, rawMagnetometerValue));
            case "buttonA":
                return (((rawButtonShakeValue[0] >> 4) & 0x1) == 0x0) ? "1" : "0";
            case "buttonB":
                return (((rawButtonShakeValue[0] >> 5) & 0x1) == 0x0) ? "1" : "0";
            case "shake":
                return ((rawButtonShakeValue[0] & 0x1) == 0x0) ? "0" : "1";
            case "screenUp":
                return rawAccelerometerValue[2] > 51 ? "1" : "0";
            case "screenDown":
                return rawAccelerometerValue[2] < -51 ? "1" : "0";
            case "tiltLeft":
                return rawAccelerometerValue[0] > 51 ? "1" : "0";
            case "tiltRight":
                return rawAccelerometerValue[0] < -51 ? "1" : "0";
            case "logoUp":
                return rawAccelerometerValue[1] > 51 ? "1" : "0";
            case "logoDown":
                return rawAccelerometerValue[1] < -51 ? "1" : "0";
            case "battery":
                double batteryVoltage = rawBatteryValue[0] * 0.037;
                if (batteryVoltage > 4.7) {
                    return "Good";
                } else if (batteryVoltage > 3.3) {
                    return "Warning";
                } else {
                    return "Bad";
                }
            default:
                return Double.toString(DeviceUtil.RawToKnob(rawSensorValue));
        }
    }

    private byte[] startPollingSensors() {
        return conn.writeBytesWithResponse(new byte[]{READ_ALL_CMD, 'g'});
    }

    private void stopPollingSensors() {
        conn.writeBytes(new byte[]{READ_ALL_CMD, 's'});
    }

    private boolean setRbSOOutput(RobotStateObject oldobj, RobotStateObject newobj, int... values) {
        try {
            lock.tryLock(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
            AtomicInteger count = new AtomicInteger(0);
            while (!newobj.equals(oldobj)) {
                if (count.incrementAndGet() > 1) break;
                doneSending.await(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
            }
            if (newobj.equals(oldobj)) {
                newobj.setValue(values);
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

    /**
     * Resets all hummingbit peripherals to their default values.
     * <p>
     * Sending a {@value #STOP_PERIPH_CMD} should achieve the same
     * thing on legacy firmware.
     *
     * @return True if succeeded in changing state, false otherwise
     */
    public boolean stopAll() {
        try {
            lock.tryLock(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
            while (!statesEqual()) {
                doneSending.await(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
            }
            if (statesEqual()) {
                newState.resetAll();
                newMBState.resetAll();
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

    /**
     * Returns whether or not this device is connected
     *
     * @return True if connected, false otherwise
     */
    public boolean isConnected() {
        return conn.isConnected();
    }

    public void setConnected() {
        DISCONNECTED = false;
    }
    /**
     * Disconnects the device
     */
    public void disconnect() {
        if (!DISCONNECTED) {
            if (ATTEMPTED) {
                forceDisconnect();
                return;
            }
            ATTEMPTED = true;
            conn.writeBytes(new byte[]{TERMINATE_CMD});
            newMBState.resetAll();
            AndroidSchedulers.from(sendThread.getLooper()).shutdown();
            sendThread.getLooper().quit();
            if (sendDisposable != null && !sendDisposable.isDisposed())
                sendDisposable.dispose();
            sendThread.quitSafely();
            AndroidSchedulers.from(monitorThread.getLooper()).shutdown();
            monitorThread.getLooper().quit();
            if (monitorDisposable != null && !monitorDisposable.isDisposed())
                monitorDisposable.dispose();
            monitorThread.quitSafely();
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
    }

    public void forceDisconnect() {
        if (!DISCONNECTED) {
            ATTEMPTED = false;
            if (conn != null) {
                conn.removeRxDataListener(this);
                conn.disconnect();
            }
            AndroidSchedulers.from(sendThread.getLooper()).shutdown();
            sendThread.getLooper().quit();
            if (sendDisposable != null && !sendDisposable.isDisposed())
                sendDisposable.dispose();
            sendThread.quitSafely();
            AndroidSchedulers.from(monitorThread.getLooper()).shutdown();
            monitorThread.getLooper().quit();
            if (monitorDisposable != null && !monitorDisposable.isDisposed())
                monitorDisposable.dispose();
            monitorThread.quitSafely();
            DISCONNECTED = true;
        }
    }
    @Override
    public void onRXData(byte[] newData) {
        synchronized (rawSensorValuesLock) {
            this.rawSensorValues = newData;
        }
    }

    public String getMacAddress() {
        try {
            return conn.getBLEDevice().getAddress();
        } catch (NullPointerException e) {
            Log.e(TAG, "Error getting hummingbit mac address: " + e.getMessage());
            return null;
        }
    }

    public String getName() {
        try {
            return NamingHandler.GenerateName(mainWebViewContext, conn.getBLEDevice().getAddress());
        } catch (NullPointerException e) {
            Log.e(TAG, "Error getting hummingbit name: " + e.getMessage());
            return null;
        }
    }

    public String getGAPName() {
        try {
            return conn.getBLEDevice().getName();
        } catch (NullPointerException e) {
            Log.e(TAG, "Error getting hummingbit gap name: " + e.getMessage());
            return null;
        }
    }

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

    public boolean hasLatestFirmware() {
        try {
            microBitVersion = (int) cfresponse[1];
            SMDVersion = (int) cfresponse[2];
            if (microBitVersion == (int) latestMicroBitVersion && SMDVersion == (int) latestSMDVersion) {
                return true;
            } else {
                return false;
            }
        } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            Log.e(TAG, "Hummingbit firmware version: " + e.getMessage());
            return false;
        }
    }

    public boolean hasMinFirmware() {
        return true;
    }
}
