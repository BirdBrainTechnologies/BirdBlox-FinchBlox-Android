package com.birdbraintechnologies.birdblox.Robots.RobotStates;

import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.HBitBuzzer;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.HBitServo;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.LED;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.TriLED;

import java.util.Arrays;

public class HBitState extends RobotState<HBitState>{
    private LED[] leds;
    private TriLED[] trileds;
    private HBitServo[] hbitservos;
    private HBitBuzzer[] hbitbuzzers;


    public HBitState() {
        leds = new LED[3];
        trileds = new TriLED[2];
        hbitservos = new HBitServo[4];
        hbitbuzzers = new HBitBuzzer[1];

        for (int i = 0; i < leds.length; i++) leds[i] = new LED();
        for (int i = 0; i < trileds.length; i++) trileds[i] = new TriLED();
        for (int i = 0; i < hbitservos.length; i++) hbitservos[i] = new HBitServo();
        for (int i = 0; i < hbitbuzzers.length; i++) hbitbuzzers[i] = new HBitBuzzer();
    }

    public HBitState(byte led1, byte led2, byte led3, byte triled1r, byte triled1g, byte triled1b, byte triled2r, byte triled2g, byte triled2b, byte servo1, byte servo2, byte servo3, byte servo4, byte buzzerFreqHigh, byte buzzerFreqLow, byte buzzerTimeHigh, byte buzzerTimeLow) {
        leds = new LED[3];
        trileds = new TriLED[2];
        hbitservos = new HBitServo[4];

        leds[0] = new LED(led1);
        leds[1] = new LED(led2);
        leds[2] = new LED(led3);

        trileds[0] = new TriLED(triled1r, triled1g, triled1b);
        trileds[1] = new TriLED(triled2r, triled2g, triled2b);
        hbitservos[0] = new HBitServo(servo1);
        hbitservos[1] = new HBitServo(servo2);
        hbitservos[2] = new HBitServo(servo3);
        hbitservos[3] = new HBitServo(servo4);
        hbitbuzzers[0] = new HBitBuzzer((short)(((buzzerFreqHigh & 0xFF) << 8) | (buzzerFreqLow & 0xFF)),(short)(((buzzerTimeHigh & 0xFF) << 8) | (buzzerTimeLow & 0xFF)));
    }

    public LED getLED(int port) {
        if (1 <= port && port <= leds.length)
            return leds[port - 1];
        return null;
    }

    public TriLED getTriLED(int port) {
        if (1 <= port && port <= trileds.length)
            return trileds[port - 1];
        return null;
    }

    public HBitServo getHBitServo(int port) {
        if (1 <= port && port <= hbitservos.length)
            return hbitservos[port - 1];
        return null;
    }

    public HBitBuzzer getHBBuzzer(int port) {
        return hbitbuzzers[0];
    }

    /**
     * Compares the current ('this') HBitState object with another HBitState object for equality.
     *
     * @param hbs The other HBitState object.
     * @return Returns true if they're equal (all their attributes
     *         have the same values), false otherwise.
     */
    @Override
    public synchronized boolean equals_helper(HBitState hbs) {
        return Arrays.equals(leds, hbs.leds) && Arrays.equals(trileds, hbs.trileds) &&
                Arrays.equals(hbitservos, hbs.hbitservos) && Arrays.equals(hbitbuzzers, hbs.hbitbuzzers);
    }


    /**
     * Compares the current ('this') HBitState object with another object for equality.
     *
     * @param hbs The other object.
     * @return Returns true if they're equal (they're both HBitState objects, and all
     *         their attributes have the same values), false otherwise.
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
        return equals_helper((HBitState) hbs);
    }


    /**
     * Copies all attributes of the input HBitState into the current ('this') HBitState.
     *
     * @param source The HBitState from which the attributes are copied.
     */
    @Override
    public synchronized void copy(HBitState source) {
        for (int i = 0; i < leds.length; i++) {
            leds[i].setValue(source.leds[i].getIntensity());
        }
        for (int i = 0; i < trileds.length; i++) {
            trileds[i].setValue(source.trileds[i].getRGB());
        }
        for (int i = 0; i < hbitservos.length; i++) {
            hbitservos[i].setValue(source.hbitservos[i].getConfig());
        }
        for (int i = 0; i < hbitbuzzers.length; i++) {
            hbitbuzzers[i].setValue((int)source.hbitbuzzers[i].getFrequency(),(int)source.hbitbuzzers[i].getDuration());
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
        all[0] = (byte) 0xCA;
        // Now, we send the other bytes in the order shown below:
        all[1] = leds[0].getIntensity();
        all[2] = (byte) 0xFF;

        all[3] = trileds[0].getRed();
        all[4] = trileds[0].getGreen();
        all[5] = trileds[0].getBlue();
        all[6] = trileds[1].getRed();
        all[7] = trileds[1].getGreen();
        all[8] = trileds[1].getBlue();

        all[9] = hbitservos[0].getConfig();
        all[10] = hbitservos[1].getConfig();
        all[11] = hbitservos[2].getConfig();
        all[12] = hbitservos[3].getConfig();

        all[13] = leds[1].getIntensity();
        all[14] = leds[2].getIntensity();

        all[15] = (byte) ((hbitbuzzers[0].getFrequency() >> 8) & 0xFF);
        all[16] = (byte) (hbitbuzzers[0].getFrequency() & 0xFF);
        all[17] = (byte) ((hbitbuzzers[0].getDuration() >> 8) & 0xFF);
        all[18] = (byte) (hbitbuzzers[0].getDuration() & 0xFF);
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
        for (int i = 0; i < leds.length; i++) leds[i] = new LED();
        for (int i = 0; i < trileds.length; i++) trileds[i] = new TriLED();
        for (int i = 0; i < hbitservos.length; i++) hbitservos[i] = new HBitServo();
        for (int i = 0; i < hbitbuzzers.length; i++) hbitbuzzers[i] = new HBitBuzzer();
    }

}
