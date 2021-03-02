package com.birdbraintechnologies.birdblox.Robots;

import android.bluetooth.BluetoothDevice;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import com.birdbraintechnologies.birdblox.Bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotState;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.RobotStateObject;
import com.birdbraintechnologies.birdblox.Util.NamingHandler;
import com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler;

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
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.robotsToConnect;
import static io.reactivex.android.schedulers.AndroidSchedulers.from;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public abstract class Robot<T1 extends RobotState<T1>, T2 extends RobotState<T2>> implements UARTConnection.RXDataListener {

    public final String TAG = this.getClass().getSimpleName();

    protected static final int COMMAND_TIMEOUT_IN_MILLIS = 5000;
    private static final int START_SENDING_INTERVAL_IN_MILLIS = 0;
    private static final int SETALL_INTERVAL_IN_MILLIS = 48;//32;//64;//32;
    private static final int MONITOR_CONNECTION_INTERVAL_IN_MILLIS = 1000;
    private static final int MAX_NO_CF_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS = 15000;
    private static final int MAX_NO_NORMAL_RESPONSE_BEFORE_DISCONNECT_IN_MILLIS = 3000;
    private static final int SEND_ANYWAY_INTERVAL_IN_MILLIS = 2000; //Not sure we really need to send anyway at all...
    private static final int MIN_COMMAND_INTERVAL = 15; //Since it is possible to need to send 3 commands in one setall interval, this value must be no more than 1/3 the setall interval.

    protected T1 oldPrimaryState;
    protected T1 newPrimaryState;
    protected T2 oldSecondaryState;
    protected T2 newSecondaryState;
    protected boolean DISCONNECTED = false;
    private boolean ATTEMPTED = false; //Are we in the middle of attempting to disconnect?

    private UARTConnection conn;
    private final String fancyName;
    private final String macAddress;
    private final String gapName;
    public final RobotType type;
    //protected byte[] rawSensorValues;
    protected byte[] rawSensorValues = new byte[20];
    protected final Object rawSensorValuesLock = new Object();
    private String last_battery_status = "";
    protected boolean sending = false;
    private boolean isCalibratingCompass = false;

    protected final ReentrantLock lock;
    protected final Condition doneSending;
    private final HandlerThread sendThread;
    private final HandlerThread monitorThread;
    private Disposable sendDisposable;
    private Disposable monitorDisposable;

    private AtomicBoolean cf; //do you need to check firmware?
    private AtomicLong cf_sent; //when was the check firmware command sent?
    protected byte[] cfresponse; //check firmware command response
    private final boolean cfWithResponse;

    private AtomicLong last_sent;
    private AtomicLong last_successfully_sent;

    protected AtomicBoolean FORCESEND = new AtomicBoolean(false); //Make sure the print string is sent (even if it is the same as the last one)
    protected AtomicBoolean CALIBRATE = new AtomicBoolean(false); //Should start compass calibration
    protected AtomicBoolean RESETENCODERS = new AtomicBoolean(false);


//TODO: Make sure no two commands are sent within 10ms of each other.

    public Robot(final UARTConnection conn, RobotType type, boolean cfWithResponse) {
        this.conn = conn;
        this.type = type;
        this.cfWithResponse = cfWithResponse;

        BluetoothDevice device = conn.getBLEDevice();
        if (device != null) {
            macAddress = device.getAddress();
            fancyName = NamingHandler.GenerateName(mainWebViewContext, macAddress);
            gapName = device.getName();
        } else {
            macAddress = "";
            fancyName = "";
            gapName = "";
            Log.e(TAG, "Failed to get device from bluetooth connection");
        }

        lock = new ReentrantLock();
        doneSending = lock.newCondition();
        cf = new AtomicBoolean(true);
        last_sent = new AtomicLong(System.currentTimeMillis());
        last_successfully_sent = new AtomicLong(System.currentTimeMillis());

        this.conn.addRxDataListener(this);

        sendThread = new HandlerThread("SendThread");
        if (!sendThread.isAlive())
            sendThread.start();
        from(sendThread.getLooper());
        Runnable sendRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    //Log.d(TAG, "Attempting to aquire lock...");
                    if( lock.tryLock(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS) ) {
                        //Log.d(TAG, "Lock aquired");
                        sendToRobot();
                        doneSending.signal();
                    }
                } catch (NullPointerException | InterruptedException | IllegalMonitorStateException e) {
                    Log.e("SENDHBSIG", "Signalling failed " + e.getMessage());
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        //Log.d(TAG, "Lock released");
                    }
                }
            }
        };
        sendDisposable = from(sendThread.getLooper()).schedulePeriodicallyDirect(sendRunnable,
                START_SENDING_INTERVAL_IN_MILLIS, SETALL_INTERVAL_IN_MILLIS, TimeUnit.MILLISECONDS);


        monitorThread = new HandlerThread("MonitorThread");
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
                        runJavascript("CallbackManager.robot.updateBatteryStatus('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode("4") + "');");
                        new Thread() {
                            @Override
                            public void run() {
                                super.run();
                                String macAddr = getMacAddress();
                                //RobotRequestHandler.disconnectFromRobot(type, macAddr);
                                RobotRequestHandler.disconnectFromRobot(macAddr);
                                addToReconnect();
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

    // **** Abstract Methods ****

    /**
     * Notifiy the frontend that this robot is incompatible and should be disconnected. Should call
     * CallbackManager.robot.disconnectIncompatible
     */
    protected abstract void notifyIncompatible();

    /**
     * Handle frontend requests to set outputs on this robot
     * @param outputType - Defines which output to set
     * @param args - Arguments defining how the output should be set
     * @return - true if the output has been set successfully
     */
    public abstract boolean setOutput(String outputType, Map<String, List<String>> args);

    /**
     * Do any cleanup that needs to be done when a new output value is set.
     * @param newobj RobotStateObject being set
     */
    protected abstract void setOutputHelper(RobotStateObject newobj);

    /**
     * Read the value of the sensor at the given port and returns the formatted value according to
     * sensor type.
     * @param sensorType - Defines what type of sensor is being read
     * @param portString - Which port the sensor is connected to
     * @param axisString - axis or position requested
     * @return A string representing the value of the sensor
     */
    public abstract String readSensor(String sensorType, String portString, String axisString);

    /**
     * @return true if the current firmware version is at least the minimum
     */
    public abstract boolean hasMinFirmware();

    /**
     * @return true if the current firmware version is the latest.
     */
    public abstract boolean hasLatestFirmware();

    /**
     * @return current hardware version string
     */
    public abstract String getHardwareVersion();

    /**
     * @return the requested command specific to this robot type
     */
    public abstract byte[] getFirmwareCommand();
    public abstract byte[] getCalibrateCommand();
    public abstract byte[] getResetEncodersCommand();
    public abstract byte[] getStartPollCommand();
    public abstract byte[] getStopPollCommand();
    public abstract byte[] getTerminateCommand();
    public abstract byte[] getStopAllCommand();

    /**
     * Return constants required to set the battery values. May return null if battery monitoring
     * is not enabled for this robot type.
     * @return [index, offset, rawToVoltage, greenThresh, yellowThresh]
     */
    public abstract double[] getBatteryConstantsArray();

    /**
     * @return index of compass calibration flag
     */
    public abstract int getCompassIndex();

    /**
     * Send the secondary state to the robot if there is one. This should be the led array state
     * for micro:bit based devices.
     * @param delayInMillis
     */
    protected abstract void sendSecondaryState(int delayInMillis);

    /**
     * Add this robot to the appropriate autoreconnect list
     */
    //protected abstract void addToReconnect();

    // **** Superclass Methods ****

    public synchronized boolean primaryStatesEqual() {
        return oldPrimaryState.equals(newPrimaryState);
    }
    public synchronized boolean secondaryStatesEqual() {
        return oldSecondaryState.equals(newSecondaryState);
    }

    /**
     * Actually sends the commands to the physical Robot,
     * based on certain conditions.
     */
    private synchronized void sendToRobot() {

        if (cf.get()) {
            if (cfWithResponse) {
                cfresponse = sendCommandAndGetResponse(getFirmwareCommand());
                if (cf_sent == null) { //Only record the time we first request firmware so we can time out if necessary
                    cf_sent = new AtomicLong(System.currentTimeMillis());
                }
            } else if (cf_sent == null) {
                boolean success = sendCommand(getFirmwareCommand());
                Log.d(TAG, "write firmware bytes? " + success);
                if (success) {
                    cf_sent = new AtomicLong(System.currentTimeMillis());
                } else {
                    return;
                }
            }

            if (cfresponse != null && cfresponse.length > 0) {
                // Successfully received check firmware response
                cf.set(false);

                if (hasV2Microbit()) {
                    Log.d(TAG, "V2 microbit found!");
                    runJavascript("CallbackManager.robot.updateHasV2Microbit('" + bbxEncode(getMacAddress()) + "', 'true')");
                } else {
                    Log.d(TAG, "NO V2 microbit found!");
                    runJavascript("CallbackManager.robot.updateHasV2Microbit('" + bbxEncode(getMacAddress()) + "', 'false')");
                }

                if (cfWithResponse) {
                    Log.d(TAG, "About to start sensor polling with response.");
                    rawSensorValues = sendCommandAndGetResponse(getStartPollCommand());
                } else {
                    sendCommand(getStartPollCommand());
                }

                if (!hasMinFirmware()) {
                    cf.set(true);
                    notifyIncompatible();
                    disconnect();
                } else if (!hasLatestFirmware()) {
                    runJavascript("CallbackManager.robot.updateFirmwareStatus('" + bbxEncode(getMacAddress()) + "', 'old')");
                }

            } else if (cf_sent.get() + 2000 > System.currentTimeMillis()) {
                Log.d(TAG, "waiting for a cf response...");
            } else {
                Log.e(TAG, "Timed out waiting for firmware version.");
                cf.set(true);
                notifyIncompatible();
                disconnect();
            }
            return;
        }

        // Not CF
        if (CALIBRATE.get()) {
            CALIBRATE.set(false);
            if (sendCommand(getCalibrateCommand())) {
                SystemClock.sleep(200);//TODO: remove and set flag in future?
                isCalibratingCompass = true;
            }
            return;
        }
        if (RESETENCODERS.get()) {
            RESETENCODERS.set(false);
            //if (sendCommand(getResetEncodersCommand())) {
                //SystemClock.sleep(200);//TODO: what does this do to the whole setall thing? I think this can be removed because the block already sleeps in the frontend.
            //}
            sendInFuture(getResetEncodersCommand(), MIN_COMMAND_INTERVAL * 2);
            //return;//TODO: if we return here, we can't signal doneSending because states aren't reset.
        }

        if (!secondaryStatesEqual() || FORCESEND.get()) {
            Log.d(TAG, "sending secondary state...");
            sendSecondaryState(MIN_COMMAND_INTERVAL);
            FORCESEND.set(false);
        }

        if (!primaryStatesEqual()) {
            //Log.d(TAG, "writing new setall state");
            byte[] cmd = newPrimaryState.setAll();
            oldPrimaryState.copy(newPrimaryState);
            sendInFuture(cmd, MIN_COMMAND_INTERVAL);
        } else {
            // Not currently sending, and oldState and newState are the same
            // TODO: This can probably be eliminated - but must modify monitor thread first
            if (System.currentTimeMillis() - last_sent.get() >= SEND_ANYWAY_INTERVAL_IN_MILLIS) {
                if (sendCommand(newPrimaryState.setAll())) {
                    oldPrimaryState.copy(newPrimaryState);
                }
            }
        }
    }
    //TODO: do we need to aquire the lock here?
    private void stopPollingSensors() {
        conn.writeBytes(getStopPollCommand());
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getName() {
        return fancyName;
    }

    public String getGAPName() {
        return gapName;
    }

    public void setConnected() {
        DISCONNECTED = false;
    }

    public boolean hasV2Microbit() {
        return (cfresponse != null && cfresponse.length > 3 && cfresponse[3] == 0x22);
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

            //TODO: aquire lock?
            newPrimaryState.resetAll();
            newSecondaryState.resetAll();
            sendCommand(getTerminateCommand());

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
                    Log.e(TAG, "Sleep error: " + e.getMessage());
                }
                conn.disconnect();
            }
            ATTEMPTED = false;
            DISCONNECTED = true;
        }
    }
    public void forceDisconnect() {  //TODO: Why do we need this? What problem were they trying to solve?
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
            addToReconnect();
            DISCONNECTED = true;
        }
    }

    protected void addToReconnect() {
        synchronized (robotsToConnect) {
            robotsToConnect.add(gapName);
        }
    }

    public boolean getDisconnected() {
        return DISCONNECTED;
    }

    public boolean isConnected() {
        return conn.isConnected();
    }

    protected boolean setRbSOOutput(RobotStateObject oldobj, RobotStateObject newobj, int... values) {
        try {
            if (lock.tryLock(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS)) {
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
                    setOutputHelper(newobj);

                    if (lock.isHeldByCurrentThread()) {
                        //doneSending.signal();  //TODO: do we need this or not?
                        lock.unlock();
                    }
                    return true;
                }
            } else {
                Log.e(TAG, "Failed to aquire lock when setting state.");
            }
        } catch (InterruptedException | IllegalMonitorStateException | IllegalStateException | IllegalThreadStateException e) {
            Log.e(TAG, "Error setting robot output: " + e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread())
                lock.unlock();
        }
        return false;
    }

    protected boolean sendCommand(byte[] command) {
        if (command == null) { return false; }
        pauseIfNeeded();

        boolean success = conn.writeBytes(command);
        logCommandSentTime(success, command);
        return success;
    }

    private byte[] sendCommandAndGetResponse(byte[] command) {
        if (command == null) { return null; }
        pauseIfNeeded();

        byte[] response = conn.writeBytesWithResponse(command);
        logCommandSentTime(response != null, command);
        return response;
    }

    private void pauseIfNeeded() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastSent = currentTime - last_sent.get();
        if (timeSinceLastSent < MIN_COMMAND_INTERVAL) {
            try {
                Log.d(TAG, "sleeping for " + (MIN_COMMAND_INTERVAL - timeSinceLastSent));
                Thread.sleep(MIN_COMMAND_INTERVAL - timeSinceLastSent);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error during sleep: " + e.getMessage());
            }
        }
    }

    private void logCommandSentTime(boolean success, byte[] cmd) {
        long currentTime = System.currentTimeMillis();
        if (success) {
            last_successfully_sent.set(currentTime);
            runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
            //Log.d(TAG, "Command successfully sent: " + cmd[0]);
        } else {
            Log.e(TAG, "Failed to send command (" + cmd[0] + ") to " + this.getName());
        }
        last_sent.set(currentTime);
    }

    protected void sendInFuture(final byte[] command, int delayInMillis) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "Sending delayed command " + command[0]);
                sendCommand(command);
            }
        }, delayInMillis);
    }

    /**
     * Resets all robot peripherals to their default values.
     *
     * @return True if succeeded in changing state, false otherwise
     */
    public boolean stopAll() {
        try {
            lock.tryLock(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
            while (!primaryStatesEqual() || !secondaryStatesEqual()) {
                doneSending.await(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
            }
            if (primaryStatesEqual() && secondaryStatesEqual()) {
                sendCommand(getStopAllCommand());
                newPrimaryState.resetAll();
                newSecondaryState.resetAll();
                if (lock.isHeldByCurrentThread()) {
                    doneSending.signal();
                    lock.unlock();
                }
                return true;
            } else {
                Log.e(TAG, "stopAll failed. States were never equal.");
            }
        } catch (InterruptedException | IllegalMonitorStateException | IllegalStateException | IllegalThreadStateException e) {
            Log.e(TAG, "Error during stopAll: " + e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread())
                lock.unlock();
        }
        return false;
    }

    // **** UARTConnection.RXDataListener Methods ****

    @Override
    public void onRXData(byte[] newData) {
        synchronized (rawSensorValuesLock) {
            if (cf.get()){
                this.cfresponse = newData;
                return;
            }
            this.rawSensorValues = newData;
            //Log.d(TAG, "motors rawData updated to " + (newData[4] < 0));
            //Log.d(TAG, "rawData updated to " + newData);

            double[] battArray = getBatteryConstantsArray();
            if (battArray != null) {
                String curBatteryStatus = "";
                int index = (int)battArray[0];
                if (hasV2Microbit() && type == RobotType.Finch) {
                    int status = newData[index] & 0x3;
                    curBatteryStatus = Integer.toString(status);
                    //Log.d(TAG, "battery status " + curBatteryStatus + " from " + newData[index]);
                } else {
                    double batteryVoltage = ((newData[index] & 0xFF) + battArray[1]) * battArray[2];
                    if (batteryVoltage > battArray[3]) { //Green threshold
                        curBatteryStatus = "2";
                    } else if (batteryVoltage > battArray[4]) { //Yellow threshold
                        curBatteryStatus = "1";
                    } else {
                        curBatteryStatus = "0";
                    }
                }
                if (!curBatteryStatus.equals(last_battery_status)) {
                    last_battery_status = curBatteryStatus;
                    runJavascript("CallbackManager.robot.updateBatteryStatus('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode(curBatteryStatus) + "');");
                }
            }

            if (isCalibratingCompass) {
                int index = getCompassIndex();
                boolean success = ((newData[index] >> 2) & 0x1) == 0x1;
                boolean failure = ((newData[index] >> 3) & 0x1) == 0x1;
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
    }

}
