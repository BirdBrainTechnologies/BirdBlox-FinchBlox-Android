package com.birdbraintechnologies.birdblocks.States.StateObjects;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public class LED {

    private byte intensity;

    public LED() {
        intensity = 0;
    }

    public LED(byte i) {
        intensity = i;
    }

    public byte getIntensity() {
        return intensity;
    }

    public void setIntensity(byte i) {
        intensity = i;
    }


}
