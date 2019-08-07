package com.birdbraintechnologies.birdblox.Robots.RobotStates;

import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.HBitBuzzer;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.TriLED;

import java.util.Arrays;

public class FinchState extends RobotState<FinchState> {

    private TriLED[] trileds;
    private HBitBuzzer[] hbitbuzzers;

    public FinchState() {

        trileds = new TriLED[5];//First beak, then 4 tail leds.
        hbitbuzzers = new HBitBuzzer[1];

        for (int i = 0; i < trileds.length; i++) trileds[i] = new TriLED();
        for (int i = 0; i < hbitbuzzers.length; i++) hbitbuzzers[i] = new HBitBuzzer();
    }

    public TriLED getTriLED(int port) {
        if (1 <= port && port <= trileds.length)
            return trileds[port - 1];
        return null;
    }


    public HBitBuzzer getBuzzer() {
        return hbitbuzzers[0];
    }


    /**
     * Compares the current ('this') HBitState object with another HBitState object for equality.
     *
     * @param hbs The other HBitState object.
     * @return Returns true if they're equal (all their attributes
     * have the same values), false otherwise.
     */
    @Override
    public synchronized boolean equals_helper(FinchState hbs) {
        return Arrays.equals(trileds, hbs.trileds) && Arrays.equals(hbitbuzzers, hbs.hbitbuzzers);
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
        return equals_helper((FinchState) hbs);
    }


    /**
     * Copies all attributes of the input HBitState into the current ('this') HBitState.
     *
     * @param source The HBitState from which the attributes are copied.
     */
    @Override
    public synchronized void copy(FinchState source) {
        for (int i = 0; i < trileds.length; i++) {
            trileds[i].setValue(source.trileds[i].getRGB());
        }
        for (int i = 0; i < hbitbuzzers.length; i++) {
            hbitbuzzers[i].setValue((int) source.hbitbuzzers[i].getFrequency(), (int) source.hbitbuzzers[i].getDuration());
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
        byte[] all = new byte[20];
        // This must always be the first byte sent for the setAll command.
        all[0] = (byte) 0xD0;
        // Now, we send the other bytes in the order shown below:
        all[1] = trileds[0].getRed();
        all[2] = trileds[0].getGreen();
        all[3] = trileds[0].getBlue();
        all[4] = trileds[1].getRed();
        all[5] = trileds[1].getGreen();
        all[6] = trileds[1].getBlue();
        all[7] = trileds[2].getRed();
        all[8] = trileds[2].getGreen();
        all[9] = trileds[2].getBlue();
        all[10] = trileds[3].getRed();
        all[11] = trileds[3].getGreen();
        all[12] = trileds[3].getBlue();
        all[13] = trileds[4].getRed();
        all[14] = trileds[4].getGreen();
        all[15] = trileds[4].getBlue();

        all[16] = (byte) ((hbitbuzzers[0].getFrequency() >> 8) & 0xFF);
        all[17] = (byte) (hbitbuzzers[0].getFrequency() & 0xFF);
        all[18] = (byte) ((hbitbuzzers[0].getDuration() >> 8) & 0xFF);
        all[19] = (byte) (hbitbuzzers[0].getDuration() & 0xFF);
        resetHBitBuzzer();
        return all;
    }


    private synchronized void resetHBitBuzzer() {
        int resetVal = 0;
        for (int i = 0; i < hbitbuzzers.length; i++) {
            hbitbuzzers[i].setValue(resetVal, resetVal);
        }
    }

    /**
     * Resets all attributes of all state objects to their default values.
     */
    @Override
    public synchronized void resetAll() {
        for (int i = 0; i < trileds.length; i++) trileds[i] = new TriLED();
        for (int i = 0; i < hbitbuzzers.length; i++) hbitbuzzers[i] = new HBitBuzzer();
    }

}
