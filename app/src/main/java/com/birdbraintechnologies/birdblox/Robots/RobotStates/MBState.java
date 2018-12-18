package com.birdbraintechnologies.birdblox.Robots.RobotStates;


import android.util.Log;

import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.HBitBuzzer;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.Pad;

import java.util.Arrays;

public class MBState extends RobotState<MBState> {

    private final String TAG = this.getClass().getName();
    private Pad[] pads;
    private HBitBuzzer[] hbitbuzzers;
    public boolean[] mode;

    public MBState() {
        pads = new Pad[3];
        hbitbuzzers = new HBitBuzzer[1];
        mode = new boolean[8];
        for (int i = 0; i < pads.length; i++) pads[i] = new Pad(i);
        for (int i = 0; i < hbitbuzzers.length; i++) hbitbuzzers[i] = new HBitBuzzer();
        for (int i = 0; i < 8; i++) mode[i] = false; //necessary?

        resetHBitBuzzer(); //TODO: should not need this here. Find out what is really going on
    }

    public Pad getPad(int port) {
        if (1 <= port && port <= pads.length)
            return pads[port - 1];
        return null;
    }

    public HBitBuzzer getHBBuzzer() {
        Log.d("TEST", "Getting the buzzer. " + hbitbuzzers.length);
        return hbitbuzzers[0];
    }

    /**
     * Compares the current ('this') MBState object with another MBState object for equality.
     *
     * @param mbs The other MBState object.
     * @return Returns true if they're equal (all their attributes
     * have the same values), false otherwise.
     */
    @Override
    public synchronized boolean equals_helper(MBState mbs) {
        return Arrays.equals(pads, mbs.pads) && Arrays.equals(hbitbuzzers, mbs.hbitbuzzers) &&
                (mode == mbs.mode);
    }


    /**
     * Compares the current ('this') MBState object with another object for equality.
     *
     * @param mbs The other object.
     * @return Returns true if they're equal (they're both MBState objects, and all
     * their attributes have the same values), false otherwise.
     */
    @Override
    public synchronized boolean equals(Object mbs) {
        // self check
        if (this == mbs)
            return true;
        // null check
        if (mbs == null)
            return false;
        // type check and cast
        if (getClass() != mbs.getClass())
            return false;
        return equals_helper((MBState) mbs);
    }


    /**
     * Copies all attributes of the input MBState into the current ('this') MBState.
     *
     * @param source The HBitState from which the attributes are copied.
     */
    @Override
    public synchronized void copy(MBState source) {
        for (int i = 0; i < pads.length; i++) {
            pads[i].setValue(source.pads[i].getIntensity());
        }
        for (int i = 0; i < hbitbuzzers.length; i++) {
            hbitbuzzers[i].setValue((int) source.hbitbuzzers[i].getFrequency(), (int) source.hbitbuzzers[i].getDuration());
        }
        mode = source.mode;
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
        all[0] = (byte) 0x90;

        for (int bit=0; bit<8; bit++){
            if (mode[bit]) {
                all[4] |= (128 >> bit);
            }
        }
        all[5] = pads[0].getIntensity();
        all[6] = pads[1].getIntensity();
        all[7] = pads[2].getIntensity();

        //If pad 0 is in buzzer mode
        if (mode[2] && !mode[3]) {
            all[1] = (byte) ((hbitbuzzers[0].getFrequency() >> 8) & 0xFF);
            all[2] = (byte) (hbitbuzzers[0].getFrequency() & 0xFF);
            all[3] = (byte) ((hbitbuzzers[0].getDuration() >> 8) & 0xFF);
            all[5] = (byte) (hbitbuzzers[0].getDuration() & 0xFF);
            resetHBitBuzzer();
        }
        String logString = "";
        for (int i = 0; i < 8; i++){
            logString += Integer.toString(all[i]  & 0xFF) + " ";
        }
        Log.d(TAG, logString);
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
        for (int i = 0; i < pads.length; i++) pads[i] = new Pad(i);
        //for (int i = 0; i < hbitbuzzers.length; i++) hbitbuzzers[i] = new HBitBuzzer();
        resetHBitBuzzer(); //TODO: Should be able to do the above like for bit. Why doesn't that work?
        for (int i = 0; i < 8; i++) mode[i] = false;
    }

}
