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
        speed = s;
    }

    public byte getSpeed() {
        return speed;
    }

    public void setSpeed(byte s) {
        speed = s;
    }

}
