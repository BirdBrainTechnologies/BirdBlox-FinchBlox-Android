package com.birdbraintechnologies.birdblox.Robots.RobotStates;


import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.HBitBuzzer;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.Pad;

import java.util.Arrays;

public class HLState extends RobotState<HLState> {

    private final String TAG = this.getClass().getSimpleName();


    public HLState() {

    }



    /**
     *
     */
    @Override
    public synchronized boolean equals_helper(HLState hls) {
        return false;
    }


    /**
     * Compares the current ('this') MBState object with another object for equality.
     *
     * @param hls The other object.
     * @return Returns true if they're equal (they're both MBState objects, and all
     * their attributes have the same values), false otherwise.
     */
    @Override
    public synchronized boolean equals(Object hls) {
        // self check
        if (this == hls)
            return true;
        // null check
        if (hls == null)
            return false;
        // type check and cast
        if (getClass() != hls.getClass())
            return false;
        return equals_helper((HLState) hls);
    }


    /**
     * Copies all attributes of the input MBState into the current ('this') MBState.
     *
     * @param source The HBitState from which the attributes are copied.
     */
    @Override
    public synchronized void copy(HLState source) {

    }

    /**
     * Generates a byte array that can be sent to the Hummingbird,
     * to set all the attributes to their current values.
     *
     * @return A byte array containing the required values for all
     * the state objects, in the order shown below.
     */
    @Override
    public synchronized byte[] setAll() {
        byte[] all = new byte[20];

        return all;
    }

    /**
     * Resets all attributes of all state objects to their default values.
     */
    @Override
    public synchronized void resetAll() {

    }

}
