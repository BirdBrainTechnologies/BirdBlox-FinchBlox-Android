package com.birdbraintechnologies.birdblox.Robots;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.Toast;

import com.birdbraintechnologies.birdblox.Bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.HBState;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.RobotStateObject;
import com.birdbraintechnologies.birdblox.Util.DeviceUtil;
import com.birdbraintechnologies.birdblox.Util.NamingHandler;

import java.io.UnsupportedEncodingException;
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
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.hummingbirdsToConnect;
import static io.reactivex.android.schedulers.AndroidSchedulers.from;

/**
 * Represents a Hummingbird device and all of its functionality: Setting outputs, reading sensors
 *
 * @author Terence Sun (tsun1215)
 * @author Shreyan Bakshi (AppyFizz)
 */
public class Hummingbird extends Robot<HBState> implements UARTConnection.RXDataListener {
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

    private static final int SETALL_INTERVAL_IN_MILLIS = 32;
    private static final int COMMAND_TIMEOUT_IN_MILLIS = 5000;
    private static final int SEND_ANYWAY_INTERVAL_IN_MILLIS = 4000;
    private static final int START_SENDING_INTERVAL_IN_MILLIS = 0;
    private static final int MONITOR_CONNECTION_INTERVAL_IN_MILLIS = 1000;
    private static final int MAX_NO_G4_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS = 10000;
    private static final int MAX_NO_NORMAL_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS = 5000;


    // This represents the firmware version 2.2a
    private static final byte minFirmwareVersion1 = 2;
    private static final byte minFirmwareVersion2 = 2;
    private static final String minFirmwareVersion3 = "a";

    // This represents the firmware version 2.2b
    private static final byte latestFirmwareVersion1 = 2;
    private static final byte latestFirmwareVersion2 = 2;
    private static final String latestFirmwareVersion3 = "b";

    private AtomicBoolean g4;
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

    private byte[] g4response;

    /**
     * Initializes a Hummingbird device
     *
     * @param conn Connection established with the Hummingbird device
     */
    public Hummingbird(final UARTConnection conn) {
        super();
        this.conn = conn;

        oldState = new HBState();
        newState = new HBState();

        g4 = new AtomicBoolean(true);
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
                long timeOut = g4.get() ? MAX_NO_G4_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS : MAX_NO_NORMAL_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS;
                if (System.currentTimeMillis() - last_successfully_sent.get() >= timeOut) {
                    try {
                        new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                String HBName = NamingHandler.GenerateName(mainWebViewContext, getMacAddress());
                                Toast.makeText(mainWebViewContext, "Connection to Hummingbird " + HBName + " timed out.", Toast.LENGTH_SHORT).show();
                            }
                        });
                        synchronized (hummingbirdsToConnect) {
                            hummingbirdsToConnect.add(getMacAddress());
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
     * Actually sends the commands to the physical Hummingbird,
     * based on certain conditions.
     */
    public synchronized void sendToRobot() {
        long currentTime = System.currentTimeMillis();
        if (g4.get()) {
            // Send here
            setSendingTrue();
            g4response = conn.writeBytesWithResponse("G4".getBytes());
            if (g4response != null && g4response.length > 0) {
                // Successfully sent G4 command
                if (last_successfully_sent != null)
                    last_successfully_sent.set(currentTime);
                g4.set(false);
                runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
                if (!hasMinFirmware()) {
                    g4.set(true);
                    runJavascript("CallbackManager.robot.disconnectIncompatible('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode(getFirmwareVersion()) + "', '" + bbxEncode(getMinFirmwareVersion()) + "')");
                    disconnect();
                } else if (!hasLatestFirmware()) {
                    runJavascript("CallbackManager.robot.updateFirmwareStatus('" + bbxEncode(getMacAddress()) + "', 'old')");
                }
            } else {
                // Sending Non-G4 command failed
            }
            setSendingFalse();
            last_sent.set(currentTime);
            return;
        }
        // Not G4
        if (isCurrentlySending()) {
            // do nothing in this case
            return;
        }
        // Not currently sending s
        if (!statesEqual()) {
            // Not currently sending, but oldState and newState are different
            // Send here
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
        } else {
            // Not currently sending, and oldState and newState are the same
            if (currentTime - last_sent.get() >= SEND_ANYWAY_INTERVAL_IN_MILLIS) {
                // Send here
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

        int port = Integer.parseInt(args.get("port").get(0));

        switch (outputType) {
            case "servo":
                return setRbSOOutput(oldState.getServo(port), newState.getServo(port), Integer.parseInt(args.get("angle").get(0)));
            case "motor":
                return setRbSOOutput(oldState.getMotor(port), newState.getMotor(port), Integer.parseInt(args.get("speed").get(0)));
            case "vibration":
                return setRbSOOutput(oldState.getVibrator(port), newState.getVibrator(port),
                        Integer.parseInt(args.get("intensity").get(0)));
            case "led":
                return setRbSOOutput(oldState.getLED(port), newState.getLED(port), Integer.parseInt(args.get("intensity").get(0)));
            case "triled":
                return setRbSOOutput(oldState.getTriLED(port), newState.getTriLED(port), Integer.parseInt(args.get("red").get(0)),
                        Integer.parseInt(args.get("green").get(0)), Integer.parseInt(args.get("blue").get(0)));
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
    public String readSensor(String sensorType, String portString) {
        byte rawSensorValue;
        synchronized (rawSensorValuesLock) {
            try {
                if (rawSensorValues == null) {
                    rawSensorValues = startPollingSensors();
                    conn.addRxDataListener(this);
                }
                int port = Integer.parseInt(portString) - 1;
                rawSensorValue = rawSensorValues[port];
            } catch (RuntimeException e) {
                Log.e(TAG, "Error getting HB sensor values: " + e.getMessage());
                return null;
            }
        }

        switch (sensorType) {
            case "distance":
                return Double.toString(DeviceUtil.RawToDist(rawSensorValue));
            case "temperature":
                return Double.toString(DeviceUtil.RawToTemp(rawSensorValue));
            case "sound":
            case "light":
            case "sensor":
            default:
                return Double.toString(DeviceUtil.RawToPercent(rawSensorValue));
        }
    }

    private byte[] startPollingSensors() {
        return conn.writeBytesWithResponse(new byte[]{READ_ALL_CMD, '5'});
    }

    private void stopPollingSensors() {
        conn.writeBytes(new byte[]{READ_ALL_CMD, '6'});
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
     * Resets all hummingbird peripherals to their default values.
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
     * Sets the angle of the servo connected to the given port
     *
     * @param port  Port number that the servo is connected to
     * @param angle Percentage [0,100] to set the intensity to
     * @return True if the command succeeded, false otherwise
     */
    public boolean setServo(int port, int angle) {
        // Compute servo angle [0,225] from angle [0,180]
        byte angleByte = clampToBounds(Math.round(angle * 1.25), 0, 225);
        return conn.writeBytes(new byte[]{SERVO_CMD, computePort(port), angleByte});
    }

    /**
     * Sets the intensity of the vibration motor connected to the given port
     *
     * @param port             Port number that the motor is connected to
     * @param intensityPercent
     * @return True if the command succeeded, false otherwise
     */
    public boolean setVibrationMotor(int port, int intensityPercent) {
        // Compute vibration intensity [0,255] from intensityPercentage
        byte intensity = clampToBounds(Math.round(intensityPercent * 2.55), 0, 255);
        return conn.writeBytes(new byte[]{VIB_MOTOR_CMD, computePort(port), intensity});
    }

    /**
     * Sets the speed of the motor connected to the given port
     *
     * @param port         Port number that the motor is connected to
     * @param speedPercent Percentage [-100,100] to set the speed to (a negative value means that
     *                     the motor spins backwards, a positive value means that the motor spins
     *                     forwards)
     * @return True if the command succeeded, false otherwise
     */
    public boolean setMotor(int port, int speedPercent) {
        // Compute direction from speedPercent parity ('0' is forward, '1' is backwards)
        byte direction = (byte) ((speedPercent >= 0) ? '0' : '1');

        // Compute absolute speed [0,255] from speedPercent [-100,100]
        byte speed = clampToBounds(Math.round(Math.abs(speedPercent) * 2.55), 0, 255);
        return conn.writeBytes(new byte[]{MOTOR_CMD, computePort(port), direction, speed});
    }

    /**
     * Sets the intensity of a LED connected to the given port
     *
     * @param port             Port number that the LED is connected to
     * @param intensityPercent Percentage [0,100] to set the intensity to
     * @return True if the command succeeded, false otherwise
     */
    public boolean setLED(int port, int intensityPercent) {
        // Compute intensity [0,255] from percentage
        byte intensity = clampToBounds(Math.round(intensityPercent * 2.55), 0, 255);
        return conn.writeBytes(new byte[]{LED_CMD, computePort(port), intensity});
    }

    /**
     * Sets the RGB values of a tri-color LED connected to the given port
     *
     * @param port     Port number that the LED is connected to
     * @param rPercent Percentage [0,100] to set R to
     * @param gPercent Percentage [0,100] to set G to
     * @param bPercent Percentage [0,100] to set B to
     * @return True if the command succeeded, false otherwise
     */
    public boolean setTriLED(int port, int rPercent, int gPercent, int bPercent) {
        // Compute rgb values [0,255] from the percentages
        byte r = clampToBounds(Math.round(rPercent * 2.55), 0, 255);
        byte g = clampToBounds(Math.round(gPercent * 2.55), 0, 255);
        byte b = clampToBounds(Math.round(bPercent * 2.55), 0, 255);
        return conn.writeBytes(new byte[]{TRI_LED_CMD, computePort(port), r, g, b});
    }

    /**
     * Turns off all LEDs and motors
     * <p>
     * This is the version of the stopAll command suited for
     * legacy firmware
     *
     * @return True if the command succeeded, false otherwise
     */
    public boolean stopAllLegacy() {
        return conn.writeBytes(new byte[]{STOP_PERIPH_CMD});
    }

    /**
     * Computes the ascii byte of the port number
     *
     * @param port Integer port to convert
     * @return Ascii representation of the port
     */
    private byte computePort(int port) {
        // TODO: Error handling for invalid ports
        // Adding 48 to a number 0-9 makes it ascii
        return (byte) ((port - 1) + 48);
    }

    /**
     * Returns a value that is bounded by min and max
     *
     * @param value Value to be clamped
     * @param min   Minimum that this value can be
     * @param max   Maximum that this value can be
     * @return Clamped value
     */
    private byte clampToBounds(long value, int min, int max) {
        if (value > max) {
            return (byte) max;
        } else if (value < min) {
            return (byte) min;
        } else {
            return (byte) value;
        }
    }

    /**
     * Returns whether or not this device is connected
     *
     * @return True if connected, false otherwise
     */
    public boolean isConnected() {
        return conn.isConnected();
    }

//    public void rename(String newName) {
//        String cmd = "+++" + "\r" + "\n";
//        Log.d("RENAME", "Request: " + Arrays.toString(cmd.getBytes()));
//        byte[] response = conn.writeBytesWithResponse(cmd.getBytes());
//        Log.d("RENAME", "Response: " + new String(response));
//        try {
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            Log.e("RENAME", e.toString());
//        }
//        cmd = RENAME_CMD + "=";
//        conn.writeBytes(cmd.getBytes());
//        cmd = newName + "\r\n";
//        conn.writeBytes(cmd.getBytes());
//        try {
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            Log.e("RENAME", e.toString());
//        }
//        cmd = "ATZ\r\n";
//        conn.writeBytes(cmd.getBytes());
//    }

    /**
     * Disconnects the device
     */
    public void disconnect() {
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
            conn.writeBytes(new byte[]{TERMINATE_CMD});
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
            Log.e(TAG, "Error getting hummingbird mac address: " + e.getMessage());
            return null;
        }
    }

    public String getName() {
        try {
            return NamingHandler.GenerateName(mainWebViewContext, conn.getBLEDevice().getAddress());
        } catch (NullPointerException e) {
            Log.e(TAG, "Error getting hummingbird name: " + e.getMessage());
            return null;
        }
    }

    public String getGAPName() {
        try {
            return conn.getBLEDevice().getName();
        } catch (NullPointerException e) {
            Log.e(TAG, "Error getting hummingbird gap name: " + e.getMessage());
            return null;
        }
    }

    public String getHardwareVersion() {
        try {
            return Byte.toString(g4response[0]) + Byte.toString(g4response[1]);
        } catch (ArrayIndexOutOfBoundsException e) {
            Log.e(TAG, "Hummingbird hardware version: " + e.getMessage());
            return null;
        }
    }

    public String getFirmwareVersion() {
        try {
            return Byte.toString(g4response[2]) + "." + Byte.toString(g4response[3]) + new String(new byte[]{g4response[4]}, "utf-8");
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

    public boolean hasMinFirmware() {
        try {
            int fw1 = (int) g4response[2];
            int fw2 = (int) g4response[3];
            String fw3 = new String(new byte[]{g4response[4]}, "utf-8");
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

    public boolean hasLatestFirmware() {
        try {
            int fw1 = (int) g4response[2];
            int fw2 = (int) g4response[3];
            String fw3 = new String(new byte[]{g4response[4]}, "utf-8");
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
}