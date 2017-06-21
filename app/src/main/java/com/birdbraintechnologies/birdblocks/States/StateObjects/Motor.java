package com.birdbraintechnologies.birdblocks.States.StateObjects;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public class Motor {

    private byte speed;

    public Motor() {
        speed = 0;
    }

    public Motor(byte s) {
        if (s < 0) {
            s = (byte) (-s);
            s |= (1 << 7);
        }
        speed = s;
    }

    public byte getSpeed() {
        return speed;
    }

    public void setSpeed(byte s) {
        if (s < 0) {
            s = (byte) (-s);
            s |= (1 << 7);
        }
        speed = s;
    }

}
