package com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects;

public class HBitServo extends RobotStateObject {

    private byte config;
    private static final int DEADZONEMIN = -10;
    private static final int DEADZONEMAX = 10;
    private static final int FREEZE = 255;

    public HBitServo() {
        config = (byte) FREEZE;
    }

    public HBitServo(byte a) {
        config = a;
    }

    public synchronized byte getConfig() {
        return config;
    }

    private synchronized void setPosition(byte a) {
        config = a;
    }

    private synchronized void setRotation(byte a) {
        config = a;
    }

    private synchronized void setPosition(int a) {
        config = clampToBounds(Math.round(a * 1.41), 0, 254);
    }

    private synchronized void setRotation(int a) {
        if (a <= DEADZONEMAX && a >= DEADZONEMIN) {
            config = (byte) FREEZE;
        } else {
            config = clampToBounds(Math.round(a * 0.23) + 122, 99, 145);
        }
    }

    @Override
    public void setValue(int... values) {
        if (values[values.length - 1] == 0) {
            setPosition(values[0]);
        } else {
            setRotation(values[0]);
        }
    }

    @Override
    public void setValue(byte... values) {
        if (values[values.length - 1] == 0) {
            setPosition(values[0]);
        } else {
            setRotation(values[0]);
        }
    }

    @Override
    public boolean equals(Object hbitServo) {
        // self check
        if (this == hbitServo)
            return true;
        // null check
        if (hbitServo == null)
            return false;
        // type check and cast
        if (getClass() != hbitServo.getClass())
            return false;
        return config == ((HBitServo) hbitServo).config;
    }

}

