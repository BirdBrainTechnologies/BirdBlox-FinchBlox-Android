package com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public class LED extends RobotStateObject{

    private byte intensity;

    public LED() {
        intensity = 0;
    }

    public LED(byte i) {
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
    public void setValue(int...values) {
        if (values.length == 1) {
            setIntensity(values[0]);
        }
    }

    @Override
    public void setValue(byte...values) {
        if (values.length == 1) {
            setIntensity(values[0]);
        }
    }

    @Override
    public boolean equals(Object led) {
        // self check
        if (this == led)
            return true;
        // null check
        if (led == null)
            return false;
        // type check and cast
        if (getClass() != led.getClass())
            return false;
        return intensity == ((LED) led).intensity;
    }


}
