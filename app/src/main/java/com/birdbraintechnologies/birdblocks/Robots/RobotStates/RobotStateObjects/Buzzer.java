package com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects;

import android.util.Log;

import java.util.Arrays;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public class Buzzer extends RobotStateObject {

    private byte volume;
    private short frequency;

    public Buzzer() {
        volume = 0;
        frequency = 0;
    }

    public Buzzer(byte v, short f) {
        volume = v;
        frequency = f;
    }

    // TODO: IMPLEMENT SETTERS CORRECTLY (CLAMP AND BYTE/SHORT/CONVERSION)

    public byte getVolume() {
        return volume;
    }

    public void setVolume(byte v) {
        this.volume = v;
    }

    public void setVolume(int v) {
        this.volume = (byte) v;
    }

    public short getFrequency() {
        return frequency;
    }

    public void setFrequency(byte f) {
        this.frequency = (short) f;
    }

    public void setFrequency(short f) {
        this.frequency = f;
    }

    public void setFrequency(int f) {
        this.frequency = (short) f;
    }

    public int[] getVF() {
        int[] vf = new int[2];
        vf[0] = (int) volume;
        vf[1] = (int) frequency;
        return vf;
    }

    private void setVF(byte v, short f) {
        volume = v;
        frequency = f;
    }

    private void setVF(int v, int f) {
        volume = (byte) v;
        frequency = (short) f;
    }

    private void setVF(int[] vf) {
        try {
            volume = (byte) vf[0];
            frequency = (short) vf[1];
        } catch (ArrayIndexOutOfBoundsException | ArrayStoreException | NegativeArraySizeException e) {
            Log.e("Buzzer", "setVF: " + e.getMessage());
        }
    }

    @Override
    public void setValue(int... values) {
        if (values.length == 2) {
            setVF(values[0], values[1]);
        }
    }

    @Override
    public void setValue(byte... values) {
        if (values.length == 2) {
            setVF(values[0], values[1]);
        }
    }

    @Override
    public boolean equals(Object buzzer) {
        // self check
        if (this == buzzer)
            return true;
        // null check
        if (buzzer == null)
            return false;
        // type check and cast
        if (getClass() != buzzer.getClass())
            return false;
        return Arrays.equals(this.getVF(), ((Buzzer) buzzer).getVF());
    }
}
