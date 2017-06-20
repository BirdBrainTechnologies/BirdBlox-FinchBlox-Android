package com.birdbraintechnologies.birdblocks.States.StateObjects;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public class Vibrator {

    private byte intensity;

    public Vibrator() {
        intensity = 0;
    }

    public Vibrator(byte i) {
        intensity = i;
    }

    public byte getIntensity() {
        return intensity;
    }

    public void setIntensity(byte i) {
        intensity = i;
    }

}
