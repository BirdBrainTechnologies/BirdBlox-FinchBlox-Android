package com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public class Motor extends RobotStateObject{

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

    public synchronized byte getSpeed() {
        return speed;
    }

    private synchronized void setSpeed(byte s) {
        if (s < 0) {
            s = (byte) (-s);
            s |= (1 << 7);
        }
        speed = s;
    }

    // TODO: Fix this
    private synchronized void setSpeed(int s) {
        if (s < 0) {
            speed = (byte) (-s);
            speed |= (1 << 7);
        } else {
            speed = (byte) s;
        }
    }

    @Override
    public void setValue(int...values) {
        if (values.length == 1) {
            setSpeed(values[0]);
        }
    }

    @Override
    public void setValue(byte...values) {
        if (values.length == 1) {
            setSpeed(values[0]);
        }
    }

    @Override
    public boolean equals(Object motor) {
        // self check
        if (this == motor)
            return true;
        // null check
        if (motor == null)
            return false;
        // type check and cast
        if (getClass() != motor.getClass())
            return false;
        return speed == ((Motor) motor).speed;
    }

}
