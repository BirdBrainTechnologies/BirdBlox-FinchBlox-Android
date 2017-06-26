package com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public abstract class RobotStateObject {

    public abstract void setValue(int... values);

    public abstract void setValue(byte... values);

    /**
     * Returns a value that is bounded by min and max
     *
     * @param value Value to be clamped
     * @param min   Minimum that this value can be
     * @param max   Maximum that this value can be
     * @return Clamped value
     */
    protected byte clampToBounds(long value, int min, int max) {
        if (value > max) {
            return (byte) max;
        } else if (value < min) {
            return (byte) min;
        } else {
            return (byte) value;
        }
    }

}
