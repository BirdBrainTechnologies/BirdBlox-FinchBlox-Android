package com.birdbraintechnologies.birdblox.Robots.RobotStates;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

/* QUICK NOTE:-
 *
 * Terminology -
 *
 * State Object: A peripheral device such as the LEDs, TriLEDs, Servos,
 *               Motors, Vibrators, the Flutter's Buzzers, the Finch's Beak,
 *               etc.
 */


public abstract class RobotState<T extends RobotState<T>> {

    /**
     * Compares the current ('this') RobotState object with another RobotState object for equality.
     *
     * @param rbs The other RobotState object.
     * @return Returns true if they're equal (all their attributes
     * have the same values), false otherwise.
     */
    public abstract boolean equals_helper(T rbs);

    /**
     * Compares the current ('this') RobotState object with another object for equality.
     *
     * @param rbs The other object.
     * @return Returns true if they're equal (their types are the same, and
     * all their attributes have the same values), false otherwise.
     */
    public abstract boolean equals(Object rbs);

    /**
     * Copies all attributes of the input RobotState into the current ('this') RobotState.
     *
     * @param source The RobotState from which the attributes are copied.
     */
    public abstract void copy(T source);

    /**
     * Generates a byte array that can be sent to the Robot,
     * to set all the attributes to their current values.
     *
     * @return A byte array containing the required values for all
     * the state objects, in the order required by the Robot.
     */
    public abstract byte[] setAll();

    /**
     * Resets all attributes of all state objects to their default values.
     */
    public abstract void resetAll();

}
