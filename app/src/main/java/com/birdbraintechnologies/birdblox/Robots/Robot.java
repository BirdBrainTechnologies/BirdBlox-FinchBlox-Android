package com.birdbraintechnologies.birdblox.Robots;

import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotState;

import java.util.List;
import java.util.Map;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public abstract class Robot<T extends RobotState<T>> {

    public final String TAG = this.getClass().getSimpleName();

    protected T oldState;
    protected T newState;

    protected boolean sending;

    public Robot() {
        sending = false;
    }

    public synchronized boolean isCurrentlySending() {
        return sending;
    }

    public synchronized void toggleSendingStatus() {
        sending = !sending;
    }

    public synchronized void setSendingTrue() {
        sending = true;
    }

    public synchronized void setSendingFalse() {
        sending = false;
    }

    public synchronized boolean statesEqual() {
        return oldState.equals(newState);
    }

    /**
     * Actually sends the commands to the physical Robot,
     * based on certain conditions.
     */
    public abstract void sendToRobot();

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

    public abstract String getMacAddress();

    public abstract String getName();

    public abstract String getGAPName();

    public abstract boolean hasMinFirmware();

    public abstract boolean hasLatestFirmware();

    public abstract void setConnected();

    public abstract void disconnect();

    public abstract boolean getDisconnected();

    public abstract boolean isConnected();

}
