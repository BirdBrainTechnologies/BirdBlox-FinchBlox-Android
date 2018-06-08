package com.birdbraintechnologies.birdblox.Robots;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.Toast;

import com.birdbraintechnologies.birdblox.Bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.MBState;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.RobotStateObject;
import com.birdbraintechnologies.birdblox.Util.DeviceUtil;
import com.birdbraintechnologies.birdblox.Util.NamingHandler;

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
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.microbitsToConnect;
import static io.reactivex.android.schedulers.AndroidSchedulers.from;

public class Microbit extends Robot<MBState> implements UARTConnection.RXDataListener {
    /*
     * Command prefixes for the Microbit according to spec
     */

    private static final byte READ_ALL_CMD = 'b';
    private static final byte TERMINATE_CMD = (byte) 0xCB;
    private static final byte STOP_PERIPH_CMD = 'X';
    private static final int SETALL_INTERVAL_IN_MILLIS = 32;
    private static final int COMMAND_TIMEOUT_IN_MILLIS = 5000;
    private static final int SEND_ANYWAY_INTERVAL_IN_MILLIS = 50;
    private static final int START_SENDING_INTERVAL_IN_MILLIS = 0;
    private static final int MONITOR_CONNECTION_INTERVAL_IN_MILLIS = 1000;
    private static final int MAX_NO_CF_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS = 10000;
    private static final int MAX_NO_NORMAL_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS = 5000;

    private static byte[] FIRMWARECOMMAND = new byte[1];
    private static final byte latestHardwareMajorVersion = 0x01;
    private static final byte latestFirmwareMajorVersion = 0x00;
    private static final byte latestFirmwareMinorVersion = 0x01;


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

    /**
     * Initializes a Microbit device
     *
     * @param conn Connection established with the Microbit device
     */
    public Microbit(final UARTConnection conn) {
        super();
        this.conn = conn;

        oldState = new MBState();
        newState = new MBState();


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
                if (System.currentTimeMillis() - last_successfully_sent.get() >= timeOut) {
                    try {
                        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                String MBName = NamingHandler.GenerateName(mainWebViewContext, getMacAddress());
                                Toast.makeText(mainWebViewContext, "Connection to Microbit " + MBName + " timed out.", Toast.LENGTH_SHORT).show();
                            }
                        });
                        synchronized (microbitsToConnect) {
                            microbitsToConnect.add(getMacAddress());
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
     * Actually sends the commands to the physical Microbit,
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
                    runJavascript("CallbackManager.robot.disconnectIncompatible('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode(getFirmwareMajorVersion()) + "', '" + bbxEncode(getLatestFirmwareMinorVersion()) + "', '" + bbxEncode(getFirmwareMinorVersion()) + "', '" + bbxEncode(getFirmwareMinorVersion()) + "')");
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
        // Not currently sending
        if (!statesEqual()) {
            // Not currently sending, but oldState and newState are different
            // Send here
            setSendingTrue();
            if (conn.writeBytes(newState.setAll())) {
                // Successfully sent Non-CF command
                if (last_successfully_sent != null)
                    last_successfully_sent.set(currentTime);
                oldState.copy(newState);
                runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
            } else {
                // Sending Non-CF command failed
            }
            setSendingFalse();
            last_sent.set(currentTime);
        } else {
            // Not currently sending, and oldState and newState are the same
            if (currentTime - last_sent.get() >= SEND_ANYWAY_INTERVAL_IN_MILLIS) {
                // Send here
                setSendingTrue();
                if (conn.writeBytes(newState.setAll())) {
                    // Successfully sent Non-CF command
                    if (last_successfully_sent != null)
                        last_successfully_sent.set(currentTime);
                    oldState.copy(newState);
                    runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
                } else {
                    // Sending Non-CF command failed
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
            case "ledArray":
                String charactersInInts = args.get("ledArrayStatus").get(0);

                int[] asciCode = new int[charactersInInts.length()];
                for (int i = 0; i < charactersInInts.length(); i++) {
                    asciCode[i] = Integer.parseInt(charactersInInts.charAt(i) + "");
                }
                return setRbSOOutput(oldState.getLedArray(), newState.getLedArray(), asciCode);
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
        synchronized (rawSensorValuesLock) {
            try {
                if (rawSensorValues == null) {
                    rawSensorValues = startPollingSensors();
                    conn.addRxDataListener(this);
                }
                rawAccelerometerValue[0] = rawSensorValues[4];
                rawAccelerometerValue[1] = rawSensorValues[5];
                rawAccelerometerValue[2] = rawSensorValues[6];

                rawMagnetometerValue[0] = rawSensorValues[8];
                rawMagnetometerValue[1] = rawSensorValues[9];
                rawMagnetometerValue[2] = rawSensorValues[10];
                rawMagnetometerValue[3] = rawSensorValues[11];
                rawMagnetometerValue[4] = rawSensorValues[12];
                rawMagnetometerValue[5] = rawSensorValues[13];

            } catch (RuntimeException e) {
                Log.e(TAG, "Error getting HB sensor values: " + e.getMessage());
                return null;
            }
        }
        switch (sensorType) {
            case "magnetometer":
                return Double.toString(DeviceUtil.RawToMag(rawMagnetometerValue, axisString));
            case "accelerometer":
                return Double.toString(DeviceUtil.RawToAccl(rawAccelerometerValue, axisString));
            default:
                return "";
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
     * Resets all microbit peripherals to their default values.
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

    /**
     * Disconnects the device
     */
    public void disconnect() {
        conn.writeBytes(new byte[]{TERMINATE_CMD});
        newState.resetAll();
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
            Log.e(TAG, "Error getting microbit mac address: " + e.getMessage());
            return null;
        }
    }

    public String getName() {
        try {
            return NamingHandler.GenerateName(mainWebViewContext, conn.getBLEDevice().getAddress());
        } catch (NullPointerException e) {
            Log.e(TAG, "Error getting microbit name: " + e.getMessage());
            return null;
        }
    }

    public String getGAPName() {
        try {
            return conn.getBLEDevice().getName();
        } catch (NullPointerException e) {
            Log.e(TAG, "Error getting microbit gap name: " + e.getMessage());
            return null;
        }
    }

    public String getHardwareVersion() {
        try {
            return Byte.toString(cfresponse[0]);
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Microbit hardware version: " + e.getMessage());
            return null;
        }
    }

    public String getFirmwareMajorVersion() {
        try {
            return Byte.toString(cfresponse[1]);
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Microbit firmwareMajor version: " + e.getMessage());
            return null;
        }
    }

    public String getFirmwareMinorVersion() {
        try {
            return Byte.toString(cfresponse[2]);
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Microbit firmwareMinor version: " + e.getMessage());
            return null;
        }
    }

    public String getLatestFirmwareMinorVersion() {
        return Byte.toString(latestFirmwareMinorVersion);
    }

    public String getLatestFirmwareMajorVersion() {
        return Byte.toString(latestFirmwareMajorVersion);
    }

    public boolean hasLatestFirmware() {
        try {
            int fwMinor = (int) cfresponse[1];
            int fwMajor = (int) cfresponse[2];
            if (fwMinor == (int) latestFirmwareMinorVersion && fwMajor == (int) latestFirmwareMajorVersion) {
                return true;
            } else {
                return false;
            }
        } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
            Log.e(TAG, "Microbit firmware version: " + e.getMessage());
            return false;
        }
    }

    public boolean hasMinFirmware() {
        return true;
    }
}
