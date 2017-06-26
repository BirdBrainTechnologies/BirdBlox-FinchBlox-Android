package com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public class Servo extends RobotStateObject{

    private byte angle;

    public Servo() {
        int i = 255;
        angle = (byte) i;
    }

    public Servo(byte a) {
        angle = a;
    }

    public synchronized byte getAngle() {
        return angle;
    }

    private synchronized void setAngle(byte a) {
        angle = a;
    }

    private synchronized void setAngle(int a) {
        angle = clampToBounds(Math.round(a * 1.25), 0, 225);
    }

    @Override
    public void setValue(int...values) {
        if (values.length == 1) {
            setAngle(values[0]);
        }
    }

    @Override
    public void setValue(byte...values) {
        if (values.length == 1) {
            setAngle(values[0]);
        }
    }

    @Override
    public boolean equals(Object servo) {
        // self check
        if (this == servo)
            return true;
        // null check
        if (servo == null)
            return false;
        // type check and cast
        if (getClass() != servo.getClass())
            return false;
        return angle == ((Servo) servo).angle;
    }

}
