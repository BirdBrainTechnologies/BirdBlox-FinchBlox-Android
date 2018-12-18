package com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects;

/**
 * Created by krissie on 8/31/18.
 */

public class Pad extends RobotStateObject {
    private byte intensity;
    private int padNum;

    public Pad(int i) {
        padNum = i;
        intensity = 0;
    }

    public Pad(byte i) {
        intensity = i;
    }

    public synchronized byte getIntensity() {
        return intensity;
    }

    public synchronized int getPadNum() { return padNum; }

    private synchronized void setIntensity(byte i) {
        intensity = i;
    }

    private synchronized void setIntensity(int i) {
        intensity = clampToBounds(Math.round(i * 2.55), 0, 255);
    }

    @Override
    public void setValue(int... values) {
        if (values.length == 1) {
            setIntensity(values[0]);
        }
    }

    @Override
    public void setValue(byte... values) {
        if (values.length == 1) {
            setIntensity(values[0]);
        }
    }

    @Override
    public boolean equals(Object pad) {
        // self check
        if (this == pad)
            return true;
        // null check
        if (pad == null)
            return false;
        // type check and cast
        if (getClass() != pad.getClass())
            return false;
        return intensity == ((Pad) pad).intensity;
    }
}
