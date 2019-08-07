package com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects;

/**
 * @author Krissie Lauwers.
 */

public class Motors extends RobotStateObject {

    private byte speedL;
    private byte speedR;
    private int ticksL;
    private int ticksR;

    public Motors() {
        speedL = 0;
        speedR = 0;
        ticksL = 1;
        ticksR = 1;
    }


    public synchronized int[] getValues() {
        int[] a = {(int)speedL, ticksL, (int)speedR, ticksR};
        return a;
    }
    public synchronized byte[] getBytes() {
        byte[] leftTickBytes = getTicks(ticksL);
        byte[] rightTickBytes = getTicks(ticksR);

        byte[] bytes = new byte[8];
        bytes[0] = speedL;
        bytes[1] = leftTickBytes[0];
        bytes[2] = leftTickBytes[1];
        bytes[3] = leftTickBytes[2];
        bytes[4] = speedR;
        bytes[5] = rightTickBytes[0];
        bytes[6] = rightTickBytes[1];
        bytes[7] = rightTickBytes[2];

        return bytes;
    }
    private synchronized byte[] getTicks(int ticks){
        byte[] ticksBytes = new byte[3];
        ticksBytes[0] = (byte)( ticks >> 16 );
        ticksBytes[1] = (byte)((ticks & 0x00ff00) >> 8);
        ticksBytes[2] = (byte)(ticks & 0x0000ff);

        return ticksBytes;
    }
/*
    private synchronized void setSpeed(byte s) {
        speed = s;
    }*/

    private synchronized void setSpeed(int left, int right) {
        left = clamp(left, -100, 100);
        right = clamp(right, -100, 100);

        speedL = (byte) (Math.abs(left));
        speedR = (byte) (Math.abs(right));
        if (left > 0) { speedL += 128; }
        if (right > 0) { speedR += 128; }
    }
    private synchronized void setTicks(int left, int right){
        ticksL = left;
        ticksR = right;
    }

    @Override
    public void setValue(int... values) {
        if (values.length == 4) {
            setSpeed(values[0], values[2]);
            setTicks(values[1], values[3]);
        }
    }

    @Override
    public void setValue(byte... values) {
        if (values.length == 1) {
            //setSpeed(values[0]);
        }
    }

    @Override
    public boolean equals(Object motors) {
        // self check
        if (this == motors)
            return true;
        // null check
        if (motors == null)
            return false;
        // type check and cast
        if (getClass() != motors.getClass())
            return false;

        return (speedL == ((Motors) motors).speedL) && (speedR == ((Motors) motors).speedR) &&
                (ticksL == ((Motors) motors).ticksL) && (ticksR == ((Motors) motors).ticksR);
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
