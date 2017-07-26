package com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public class Motor extends RobotStateObject{

    private byte speed;

    public Motor() {
        speed = 0;
    }

    public Motor(byte s) {
        speed = s;
    }

    public synchronized byte getSpeed() {
        return speed;
    }

    private synchronized void setSpeed(byte s) {
        speed = s;
    }

    private synchronized void setSpeed(int s) {
        s = clamp(s, -100, 100);
        if (s < 0) {
            speed = (byte) (Math.abs(s));
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

    private static int clamp(int value, int min, int max) {
        if (value > max) {
            return max;
        } else if (value < min) {
            return min;
        } else {
            return value;
        }
    }

}
