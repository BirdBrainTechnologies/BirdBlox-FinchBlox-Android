package com.birdbraintechnologies.birdblox.Robots.RobotStates;


import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.LEDArray;

import java.util.Arrays;

public class MBState extends RobotState<MBState> {
    private LEDArray[] ledArray;

    public MBState() {
        ledArray = new LEDArray[1];
        for (int i = 0; i < ledArray.length; i++) ledArray[i] = new LEDArray();
    }

    public LEDArray getLedArray() {
        return ledArray[0];
    }

    /**
     * Compares the current ('this') HBitState object with another HBitState object for equality.
     *
     * @param hbs The other HBitState object.
     * @return Returns true if they're equal (all their attributes
     * have the same values), false otherwise.
     */
    @Override
    public synchronized boolean equals_helper(MBState hbs) {
        return Arrays.equals(ledArray, hbs.ledArray);
    }


    /**
     * Compares the current ('this') HBitState object with another object for equality.
     *
     * @param hbs The other object.
     * @return Returns true if they're equal (they're both HBitState objects, and all
     * their attributes have the same values), false otherwise.
     */
    @Override
    public synchronized boolean equals(Object hbs) {
        // self check
        if (this == hbs)
            return true;
        // null check
        if (hbs == null)
            return false;
        // type check and cast
        if (getClass() != hbs.getClass())
            return false;
        return equals_helper((MBState) hbs);
    }


    /**
     * Copies all attributes of the input HBitState into the current ('this') HBitState.
     *
     * @param source The HBitState from which the attributes are copied.
     */
    @Override
    public synchronized void copy(MBState source) {
        for (int i = 0; i < ledArray.length; i++) {
            ledArray[i].setValue(source.ledArray[i].getCharacters());
        }
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
        byte[] all = new byte[19];
        // This must always be the first byte sent for the setAll command.
        all[0] = (byte) 0xCC;
        // Now, we send the other bytes in the order shown below:

        return all;
    }

    /**
     * Resets all attributes of all state objects to their default values.
     */
    @Override
    public synchronized void resetAll() {
        for (int i = 0; i < ledArray.length; i++) ledArray[i] = new LEDArray();
    }

}
