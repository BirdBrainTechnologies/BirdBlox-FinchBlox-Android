package com.birdbraintechnologies.birdblocks.Robots;

import com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotState;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public abstract class Robot<T extends RobotState<T>> {

    protected T oldState;
    protected T newState;

    protected boolean sending;

    public Robot() {
        sending = false;
    }

//    public synchronized boolean statesEqual() {
//        return oldState.equals(newState);
//    }

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

    public synchronized boolean statesEqual() { return oldState.equals(newState);}

    /**
     * Actually sends the commands to the physical Robot,
     * based on certain conditions.
     */
    public abstract boolean sendToRobot();

}
