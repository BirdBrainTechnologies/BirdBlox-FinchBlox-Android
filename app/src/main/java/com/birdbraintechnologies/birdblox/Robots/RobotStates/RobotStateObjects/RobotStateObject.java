package com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public abstract class RobotStateObject {

    public abstract void setValue(int... values);

    public abstract void setValue(byte... values);

    /**
     * Compares the current ('this') RobotStateObject object with another object for equality.
     *
     * @param rbso The other object.
     * @return Returns true if they're equal (their types are the same, and
     * all their attributes have the same values), false otherwise.
     */
    public abstract boolean equals(Object rbso);

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
