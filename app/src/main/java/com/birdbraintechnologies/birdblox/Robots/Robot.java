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

import java.util.HashSet;
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
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.hummingbirdsToConnect;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.hummingbitsToConnect;
import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.microbitsToConnect;
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
    private static final int MIN_COMMAND_INTERVAL = 20; //Since it is possible to need to send 3 commands in one setall interval, this value must be no more than 1/3 the setall interval.

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
    private final RobotType type;
    protected byte[] rawSensorValues;
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
    private boolean cfWithResponse = false;

    private AtomicLong last_sent;
    private AtomicLong last_successfully_sent;

    protected AtomicBoolean FORCESEND = new AtomicBoolean(false);
    protected AtomicBoolean CALIBRATE = new AtomicBoolean(false);
    protected AtomicBoolean RESETENCODERS = new AtomicBoolean(false);


//TODO: Make sure no two commands are sent within 10ms of each other.

    public Robot(final UARTConnection conn, final RobotType type) {
        this.conn = conn;
        this.conn.addRxDataListener(this);
        this.type = type;

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
                    if (DISCONNECTED) {
                        return;//TODO: This does nothing.
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
                        runJavascript("CallbackManager.robot.updateBatteryStatus('" + bbxEncode(getMacAddress()) + "', '" + bbxEncode("3") + "');");
                        new Thread() {
                            @Override
                            public void run() {
                                super.run();
                                String macAddr = getMacAddress();
                                RobotRequestHandler.disconnectFromRobot(type, macAddr);
                                addToReconnect();
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

    }

    public synchronized boolean isCurrentlySending() {
        return sending;
    }

    public synchronized void setSendingTrue() {
        sending = true;
    }

    public synchronized void setSendingFalse() {
        sending = false;
    }

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
    //public abstract void sendToRobot();
    private synchronized void sendToRobot() {

        if (cf.get()) {
            if (cfWithResponse) {
                cfresponse = sendCommandAndGetResponse(getFirmwareCommand());
            } else if (cf_sent == null) {
                setSendingTrue();
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

                if (rawSensorValues == null) {
                    setSendingTrue();
                    if (cfWithResponse) {
                        rawSensorValues = sendCommandAndGetResponse(getStartPollCommand());
                    } else {
                        sendCommand(getStartPollCommand());
                    }
                }

                if (!hasLatestFirmware()) {
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
            setSendingFalse();
            return;
        }
        // Not CF
        if (isCurrentlySending()) {//TODO: do we really need this whole sending system? We have the lock...
            Log.e(TAG,"Currently sending...");
            // do nothing in this case
            return;
        }
        if (CALIBRATE.get()) {
            CALIBRATE.set(false);
            setSendingTrue();
            if (sendCommand(getCalibrateCommand())) {
                SystemClock.sleep(200);//TODO: remove and set flag in future?
                isCalibratingCompass = true;
            }
            setSendingFalse();
            return;
        }
        if (RESETENCODERS.get()) {
            RESETENCODERS.set(false);
            setSendingTrue();
            //if (sendCommand(getResetEncodersCommand())) {
                //SystemClock.sleep(200);//TODO: what does this do to the whole setall thing? I think this can be removed because the block already sleeps in the frontend.
            //}
            sendInFuture(getResetEncodersCommand(), MIN_COMMAND_INTERVAL * 2);
            setSendingFalse();
            //return;//TODO: if we return here, we can't signal doneSending because states aren't reset.
        }

        //boolean secondarySent = false;
        if (!secondaryStatesEqual() || FORCESEND.get()) {
            setSendingTrue();
            sendSecondaryState(MIN_COMMAND_INTERVAL);
            setSendingFalse();
            FORCESEND.set(false);
            //secondarySent = true;
        }

        if (!primaryStatesEqual()) {
            // Not currently sending, but oldState and newState are different
            // Send here
            /*if (secondarySent) {
                final byte[] pendingCommand = newPrimaryState.setAll();
                oldPrimaryState.copy(newPrimaryState);
                Log.d(TAG, "Just set set all states equal. Will send command soon...");
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        setSendingTrue();
                        try {
                            lock.tryLock(COMMAND_TIMEOUT_IN_MILLIS, TimeUnit.MILLISECONDS);
                            Log.d(TAG, "writing delayed new setall state");
                            sendCommand(pendingCommand);
                        } catch (NullPointerException | InterruptedException | IllegalMonitorStateException e) {
                            Log.e("SENDHBSIG", "Signalling failed " + e.getMessage());
                        } finally {
                            if (lock.isHeldByCurrentThread())
                                lock.unlock();
                        }
                        setSendingFalse();
                        Log.d(TAG, "done writing delayed new setall state");
                    }
                }, SETALL_INTERVAL_IN_MILLIS/2);
            } else {*/
                setSendingTrue();
                Log.d(TAG, "writing new setall state");
                byte[] cmd = newPrimaryState.setAll();
                //if (sendCommand(newPrimaryState.setAll())) {
                    oldPrimaryState.copy(newPrimaryState);
                //}
                sendInFuture(cmd, MIN_COMMAND_INTERVAL);
                setSendingFalse();
            //}
        } else {
            // Not currently sending, and oldState and newState are the same
            // TODO: This can probably be eliminated - but must modify monitor thread first
            if (System.currentTimeMillis() - last_sent.get() >= SEND_ANYWAY_INTERVAL_IN_MILLIS) {
                setSendingTrue();
                if (sendCommand(newPrimaryState.setAll())) {
                    oldPrimaryState.copy(newPrimaryState);
                }
                setSendingFalse();
            }
        }
    }
    //TODO: do we need to aquire the lock here?
    private void stopPollingSensors() {
        conn.writeBytes(getStopPollCommand());
    }

    /**
     * Notifiy the frontend that this robot is incompatible and should be disconnected. Should call
     * CallbackManager.robot.disconnectIncompatible
     */
    protected abstract void notifyIncompatible();

    /**
     * @param outputType
     * @param args
     * @return
     */
    public abstract boolean setOutput(String outputType, Map<String, List<String>> args);

    /**
     * @param sensorType
     * @param portString
     * @return
     */
    public abstract String readSensor(String sensorType, String portString, String axisString);

    //public abstract String getMacAddress();
    public String getMacAddress() {
        return macAddress;
    }

    //public abstract String getName();
    public String getName() {
        return fancyName;
    }

    //public abstract String getGAPName();
    public String getGAPName() {
        return gapName;
    }

    public abstract boolean hasMinFirmware();

    public abstract boolean hasLatestFirmware();

    public abstract String getHardwareVersion();

    //public abstract void setConnected();
    public void setConnected() {
        DISCONNECTED = false;
    }

    /**
     * Disconnects the device
     */
    //public abstract void disconnect();
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
    private void addToReconnect() {
        switch (type) { //TODO: Why are there different reconnect lists?
            case Microbit:
                addToHashSet(microbitsToConnect);
                break;
            case Finch:
                addToHashSet(finchesToConnect);
                break;
            case Hummingbit:
                addToHashSet(hummingbitsToConnect);
                break;
            case Hummingbird:
                addToHashSet(hummingbirdsToConnect);
                cfWithResponse = true;
                break;
            default:
                Log.e(TAG, "Robot created with undefined type " + type.toString());
        }

    }
    private void addToHashSet(HashSet<String> set) {
        synchronized (set) {
            if (!set.contains(macAddress)) {
                set.add(macAddress);
            }
        }
    }

    //public abstract boolean getDisconnected();
    public boolean getDisconnected() {
        return DISCONNECTED;
    }

    //public abstract boolean isConnected();
    public boolean isConnected() {
        return conn.isConnected();
    }

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

    public abstract int getCompassIndex();

    protected abstract void sendSecondaryState(int delayInMillis);

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
        logCommandSentTime(success);
        return success;
    }

    private byte[] sendCommandAndGetResponse(byte[] command) {
        if (command == null) { return null; }
        pauseIfNeeded();

        byte[] response = conn.writeBytesWithResponse(command);
        logCommandSentTime(response != null);
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

    private void logCommandSentTime(boolean success) {
        long currentTime = System.currentTimeMillis();
        if (success) {
            last_successfully_sent.set(currentTime);
            runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
            Log.d(TAG, "Command successfully sent");
        } else {
            Log.e(TAG, "Failed to send command to " + this.getName());
        }
        last_sent.set(currentTime);
    }

    protected void sendInFuture(final byte[] command, int delayInMillis) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
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

    // UARTConnection.RXDataListener Methods

    @Override
    public void onRXData(byte[] newData) {
        synchronized (rawSensorValuesLock) {
            if (cf.get()){
                this.cfresponse = newData;
                return;
            }
            this.rawSensorValues = newData;
            Log.d(TAG, "motors rawData updated to " + (newData[4] < 0));

            double[] battArray = getBatteryConstantsArray();
            if (battArray != null) {
                String curBatteryStatus = "";
                int index = (int)battArray[0];
                double batteryVoltage = ((newData[index] & 0xFF) + battArray[1]) * battArray[2];
                if (batteryVoltage > battArray[3]) { //Green threshold
                    curBatteryStatus = "2";
                } else if (batteryVoltage > battArray[4]) { //Yellow threshold
                    curBatteryStatus = "1";
                } else {
                    curBatteryStatus = "0";
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
