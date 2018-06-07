package com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public class Vibrator extends RobotStateObject {

    private byte intensity;

    public Vibrator() {
        intensity = 0;
    }

    public Vibrator(byte i) {
        intensity = i;
    }

    public synchronized byte getIntensity() {
        return intensity;
    }

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
    public boolean equals(Object vibrator) {
        // self check
        if (this == vibrator)
            return true;
        // null check
        if (vibrator == null)
            return false;
        // type check and cast
        if (getClass() != vibrator.getClass())
            return false;
        return intensity == ((Vibrator) vibrator).intensity;
    }

}
