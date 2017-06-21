package com.birdbraintechnologies.birdblocks.States.StateObjects;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public class Servo {

    private byte angle;

    public Servo() {
        int i = 255;
        angle = (byte) i;
    }

    public Servo(byte a) {
        angle = a;
    }

    public byte getAngle() {
        return angle;
    }

    public void setAngle(byte a) {
        angle = a;
    }

}
