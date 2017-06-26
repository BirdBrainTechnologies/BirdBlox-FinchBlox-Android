package com.birdbraintechnologies.birdblocks.Robots.RobotStates;

import com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects.LED;
import com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects.Motor;
import com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects.Servo;
import com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects.TriLED;
import com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects.Vibrator;

import java.util.Arrays;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public class HBState extends RobotState<HBState> {
    private LED[] leds;
    private TriLED[] trileds;
    private Servo[] servos;
    private Motor[] motors;
    private Vibrator[] vibrators;

    public HBState() {
        leds = new LED[4];
        trileds = new TriLED[2];
        servos = new Servo[4];
        motors = new Motor[2];
        vibrators = new Vibrator[2];

        for (LED led : leds) led = new LED();
        for (TriLED triled : trileds) triled = new TriLED();
        for (Servo servo : servos) servo = new Servo();
        for (Motor motor : motors) motor = new Motor();
        for (LED led : leds) led = new LED();
    }

    public HBState(byte led1, byte led2, byte led3, byte led4, byte triled1r, byte triled1g, byte triled1b, byte triled2r, byte triled2g, byte triled2b, byte servo1, byte servo2, byte servo3, byte servo4, byte motor1, byte motor2, byte vibrator1, byte vibrator2) {
        leds = new LED[4];
        trileds = new TriLED[2];
        servos = new Servo[4];
        motors = new Motor[2];
        vibrators = new Vibrator[2];

        leds[0] = new LED(led1);
        leds[1] = new LED(led2);
        leds[2] = new LED(led3);
        leds[3] = new LED(led4);
        trileds[0] = new TriLED(triled1r, triled1g, triled1b);
        trileds[1] = new TriLED(triled2r, triled2g, triled2b);
        servos[0] = new Servo(servo1);
        servos[1] = new Servo(servo2);
        servos[2] = new Servo(servo3);
        servos[3] = new Servo(servo4);
        motors[0] = new Motor(motor1);
        motors[1] = new Motor(motor2);
        vibrators[0] = new Vibrator(vibrator1);
        vibrators[1] = new Vibrator(vibrator2);
    }

    public LED getLED(int port) {
        if (1 <= port && port <= leds.length)
            return leds[port - 1];
        return null;
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

    public Motor getMotor(int port) {
        if (1 <= port && port <= motors.length)
            return motors[port - 1];
        return null;
    }

    public Vibrator getVibrator(int port) {
        if (1 <= port && port <= vibrators.length)
            return vibrators[port - 1];
        return null;
    }

    public byte getLEDIntensity(int port) {
        if (1 <= port && port <= leds.length)
            return leds[port - 1].getIntensity();
        return 0;
    }

    public void setLEDIntensity(int port, int intensity) {
        if (1 <= port && port <= leds.length)
            leds[port - 1].setValue(intensity);
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

    public byte getMotorSpeed(int port) {
        if (1 <= port && port <= motors.length)
            return motors[port - 1].getSpeed();
        return 0;
    }

    public void setMotorSpeed(int port, int speed) {
        if (1 <= port && speed <= motors.length)
            motors[port - 1].setValue(speed);
    }

    public byte getVibratorIntensity(int port) {
        if (1 <= port && port <= vibrators.length)
            return vibrators[port - 1].getIntensity();
        return 0;
    }

    public void setVibratorIntensity(int port, int intensity) {
        if (1 <= port && intensity <= vibrators.length)
            vibrators[port - 1].setValue(intensity);
    }

    /**
     * Compares the current ('this') HBState object with another HBState object for equality.
     *
     * @param hbs The other HBState object.
     * @return Returns true if they're equal (all their attributes
     *         have the same values), false otherwise.
     */
    @Override
    public boolean equals_helper(HBState hbs) {
        for (int i = 0; i < 4; i++) {
            if (leds[i].getIntensity() != hbs.leds[i].getIntensity())
                return false;
        }
        for (int i = 0; i < 2; i++) {
            if (!Arrays.equals(trileds[i].getRGB(), hbs.trileds[i].getRGB()))
                return false;
        }
        for (int i = 0; i < 4; i++) {
            if (servos[i].getAngle() != hbs.servos[i].getAngle())
                return false;
        }
        for (int i = 0; i < 2; i++) {
            if (motors[i].getSpeed() != hbs.motors[i].getSpeed())
                return false;
        }
        for (int i = 0; i < 2; i++) {
            if (vibrators[i].getIntensity() != hbs.vibrators[i].getIntensity())
                return false;
        }
        return true;
    }


    /**
     * Compares the current ('this') HBState object with another object for equality.
     *
     * @param hbs The other object.
     * @return Returns true if they're equal (they're both HBState objects, and all
     *         their attributes have the same values), false otherwise.
     */
    @Override
    public boolean equals(Object hbs) {
        // self check
        if (this == hbs)
            return true;
        // null check
        if (hbs == null)
            return false;
        // type check and cast
        if (getClass() != hbs.getClass())
            return false;
        return equals_helper((HBState) hbs);
    }


    /**
     * Copies all attributes of the input HBState into the current ('this') HBState.
     *
     * @param source The HBState from which the attributes are copied.
     */
    @Override
    public void copy(HBState source) {
        for (int i = 0; i < 4; i++) {
            leds[i].setValue(source.leds[i].getIntensity());
        }
        for (int i = 0; i < 2; i++) {
            trileds[i].setValue(source.trileds[i].getRGB());
        }
        for (int i = 0; i < 4; i++) {
            servos[i].setValue(source.servos[i].getAngle());
        }
        for (int i = 0; i < 2; i++) {
            motors[i].setValue(source.motors[i].getSpeed());
        }
        for (int i = 0; i < 2; i++) {
            vibrators[i].setValue(source.vibrators[i].getIntensity());
        }
    }

    /**
     * Generates a byte array that can be sent to the Hummingbird,
     * to set all the attributes to their current values.
     *
     * @return A byte array containing the required values for all
     * the state objects, in the order shown below.
     */
    @Override
    public byte[] setAll() {
        byte[] all = new byte[19];
        // This must always be the first byte sent for the setAll command.
        all[0] = (byte) 0x41;
        // Now, we send the other bytes in the order shown below:
        all[1] = trileds[0].getRed();
        all[2] = trileds[0].getGreen();
        all[3] = trileds[0].getBlue();
        all[4] = trileds[1].getRed();
        all[5] = trileds[1].getGreen();
        all[6] = trileds[1].getBlue();
        all[7] = leds[0].getIntensity();
        all[8] = leds[1].getIntensity();
        all[9] = leds[2].getIntensity();
        all[10] = leds[3].getIntensity();
        all[11] = servos[0].getAngle();
        all[12] = servos[1].getAngle();
        all[13] = servos[2].getAngle();
        all[14] = servos[3].getAngle();
        all[15] = vibrators[0].getIntensity();
        all[16] = vibrators[1].getIntensity();
        all[17] = motors[0].getSpeed();
        all[18] = motors[1].getSpeed();
        return all;
    }

    /**
     * Resets all attributes of all state objects to their default values.
     */
    @Override
    public void resetAll() {
        for (LED led : leds) led = new LED();
        for (TriLED triled : trileds) triled = new TriLED();
        for (Servo servo : servos) servo = new Servo();
        for (Motor motor : motors) motor = new Motor();
        for (LED led : leds) led = new LED();
    }

}
