package com.birdbraintechnologies.birdblocks.Robots;

import com.birdbraintechnologies.birdblocks.States.RobotState;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public abstract class Robot<T extends RobotState<T>> {

    private T oldState;
    private T newState;

    private boolean sending;

    public boolean statesEqual() {
        return oldState.equal(newState);
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

    public abstract void send();

}
