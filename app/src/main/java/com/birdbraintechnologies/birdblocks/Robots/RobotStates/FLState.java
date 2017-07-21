package com.birdbraintechnologies.birdblocks.Robots.RobotStates;

import com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects.Buzzer;
import com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects.Servo;
import com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects.TriLED;

import java.util.Arrays;

/**
 * @author AppyFizz (Shreyan Bakshi)
 */

public class FLState extends RobotState<FLState> {
    private TriLED[] trileds;
    private Servo[] servos;
    private Buzzer[] buzzers;

    public FLState() {
        trileds = new TriLED[3];
        servos = new Servo[3];
        buzzers = new Buzzer[1];

        for (int i = 0; i < trileds.length; i++) trileds[i] = new TriLED();
        for (int i = 0; i < servos.length; i++) servos[i] = new Servo();
        for (int i = 0; i < buzzers.length; i++) buzzers[i] = new Buzzer();
    }

    public FLState(byte triled1r, byte triled1g, byte triled1b, byte triled2r, byte triled2g, byte triled2b, byte triled3r, byte triled3g, byte triled3b, byte servo1, byte servo2, byte servo3, byte buzzer1v, short buzzer1f) {
        trileds = new TriLED[3];
        servos = new Servo[3];
        buzzers = new Buzzer[1];

        trileds[0] = new TriLED(triled1r, triled1g, triled1b);
        trileds[1] = new TriLED(triled2r, triled2g, triled2b);
        trileds[2] = new TriLED(triled3r, triled3g, triled3b);
        servos[0] = new Servo(servo1);
        servos[1] = new Servo(servo2);
        servos[2] = new Servo(servo3);
        buzzers[0] = new Buzzer(buzzer1v, buzzer1f);
    }

    public TriLED getTriLED(int port) {
        if (1 <= port && port <= trileds.length)
            return trileds[port - 1];
        return null;
    }

    public Servo getServo(int port) {
        if (1 <= port && port <= servos.length)
            return servos[port - 1];
        return null;
    }

    public Buzzer getBuzzer(int port) {
        if (1 <= port && port <= buzzers.length)
            return buzzers[port - 1];
        return null;
    }

    public byte[] getTriLEDRGB(int port) {
        if (1 <= port && port <= trileds.length)
            return trileds[port - 1].getRGB();
        return null;
    }

    public void setTriLEDRGB(int port, int red, int green, int blue) {
        if (1 <= port && port <= trileds.length)
            trileds[port - 1].setValue(red, green, blue);
    }

    public void setTriLEDRGB(int port, int[] rgb) {
        if (1 <= port && port <= trileds.length)
            trileds[port - 1].setValue(rgb);
    }

    public byte getServoAngle(int port) {
        if (1 <= port && port <= servos.length)
            return servos[port - 1].getAngle();
        return (byte) 255;
    }

    public void setServoAngle(int port, int angle) {
        if (1 <= port && port <= servos.length)
            servos[port - 1].setValue(angle);
    }

    public byte getBuzzerVolume(int port) {
        if (1 <= port && port <= buzzers.length)
            return buzzers[port - 1].getVolume();
        return (byte) 0;
    }

    public void setBuzzerVolume(int port, int volume) {
        if (1 <= port && port <= buzzers.length)
            buzzers[port - 1].setValue(volume);
    }

    public short getBuzzerFrequency(int port) {
        if (1 <= port && port <= buzzers.length)
            return buzzers[port - 1].getFrequency();
        return (short) 0;
    }

    public void setBuzzerFrequency(int port, int frequency) {
        if (1 <= port && port <= buzzers.length)
            buzzers[port - 1].setValue(frequency);
    }

    public int[] getBuzzerVF(int port) {
        if (1 <= port && port <= buzzers.length)
            return buzzers[port - 1].getVF();
        return null;
    }

    public void setBuzzerVF(int port, int volume, int frequency) {
        if (1 <= port && port <= buzzers.length)
            buzzers[port - 1].setValue(volume, frequency);
    }

    public void setBuzzerVF(int port, int[] vf) {
        if (1 <= port && port <= buzzers.length)
            buzzers[port - 1].setValue(vf);
    }

    /**
     * Compares the current ('this') FLState object with another FLState object for equality.
     *
     * @param fls The other FLState object.
     * @return Returns true if they're equal (all their attributes
     * have the same values), false otherwise.
     */
    @Override
    public synchronized boolean equals_helper(FLState fls) {
        return Arrays.equals(trileds, fls.trileds) && Arrays.equals(servos, fls.servos) &&
                Arrays.equals(buzzers, fls.buzzers);
    }


    /**
     * Compares the current ('this') FLState object with another object for equality.
     *
     * @param fls The other object.
     * @return Returns true if they're equal (they're both HBState objects, and all
     * their attributes have the same values), false otherwise.
     */
    @Override
    public synchronized boolean equals(Object fls) {
        // self check
        if (this == fls)
            return true;
        // null check
        if (fls == null)
            return false;
        // type check and cast
        if (getClass() != fls.getClass())
            return false;
        return equals_helper((FLState) fls);
    }


    /**
     * Copies all attributes of the input HBState into the current ('this') FLState.
     *
     * @param source The HBState from which the attributes are copied.
     */
    @Override
    public synchronized void copy(FLState source) {
        for (int i = 0; i < trileds.length; i++) {
            trileds[i].setValue(source.trileds[i].getRGB());
        }
        for (int i = 0; i < servos.length; i++) {
            servos[i].setValue(source.servos[i].getAngle());
        }
        for (int i = 0; i < buzzers.length; i++) {
            buzzers[i].setValue(source.buzzers[i].getVF());
        }
    }

    /**
     * Generates a byte array that can be sent to the Flutter,
     * to set all the attributes to their current values.
     *
     * @return A byte array containing the required values for all
     * the state objects, in the order shown below.
     */
    @Override
    public synchronized byte[] setAll() {
        // TODO: NOTE: The below commands are only hypothesized based off of the existing Hummingbird setAll command.
        // The Flutter setAll command is not implemented in the hardware yet, and no documentation for it is available.
        // So, it is very likely that the below commands will not work in practice.
        byte[] all = new byte[16];
        // This must always be the first byte sent for the setAll command.
        all[0] = (byte) 0x41;
        // Now, we send the other bytes in the order shown below:
        all[1] = trileds[0].getRed();
        all[2] = trileds[0].getGreen();
        all[3] = trileds[0].getBlue();
        all[4] = trileds[1].getRed();
        all[5] = trileds[1].getGreen();
        all[6] = trileds[1].getBlue();
        all[7] = trileds[2].getRed();
        all[8] = trileds[2].getGreen();
        all[9] = trileds[2].getBlue();
        all[10] = servos[0].getAngle();
        all[11] = servos[1].getAngle();
        all[12] = servos[2].getAngle();
        all[13] = buzzers[0].getVolume();
        // TODO: Pick one of the two (depending on the robot's requirements), and discard the other.
        // Little Endian
        all[14] = (byte) buzzers[0].getFrequency();
        all[15] = (byte) (buzzers[0].getFrequency() >> 8);
        // Big Endian
        all[14] = (byte) (buzzers[0].getFrequency() >> 8);
        all[15] = (byte) buzzers[0].getFrequency();
        return all;
    }

    /**
     * Resets all attributes of all state objects to their default values.
     */
    @Override
    public synchronized void resetAll() {
        for (int i = 0; i < trileds.length; i++) trileds[i] = new TriLED();
        for (int i = 0; i < servos.length; i++) servos[i] = new Servo();
        for (int i = 0; i < buzzers.length; i++) buzzers[i] = new Buzzer();
    }


}
