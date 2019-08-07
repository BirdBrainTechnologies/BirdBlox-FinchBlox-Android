package com.birdbraintechnologies.birdblox.Robots.RobotStates;

import android.util.Log;

import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.LEDArray;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.Motors;

import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * State to keep track of the Finch's motors. Also handles the micro:bit led array.
 *
 * Created by krissie on 6/3/19.
 */

public class FinchMotorState extends RobotState<FinchMotorState> {

    private final String TAG = this.getClass().getSimpleName();

    private Motors motors;
    private LEDArray ledArray;
    private boolean sendMotors;
    private boolean sendLEDArray;

    public FinchMotorState() {
        motors = new Motors();
        ledArray = new LEDArray();
        sendMotors = false;
        sendLEDArray = false;
    }

    public Motors getMotors() { return motors; }

    public LEDArray getLedArray() {
        return ledArray;
    }

    public boolean getSendMotors() { return sendMotors; }

    public synchronized void setSendMotors(boolean sm){
        sendMotors = sm;
    }
    public synchronized void setSendLEDArray(boolean sl){
        sendLEDArray = sl;
    }

    /**
     * Compares the current ('this') FinchMotorState object with another FinchMotorState object for equality.
     *
     * @param fms The other FinchMotorState object.
     * @return Returns true if they're equal (all their attributes
     * have the same values), false otherwise.
     */
    @Override
    public synchronized boolean equals_helper(FinchMotorState fms) {
        return (motors.equals(fms.motors)) && (ledArray.equals(fms.ledArray));
    }

    /**
     * Compares the current ('this') FinchMotorState object with another object for equality.
     *
     * @param fms The other object.
     * @return Returns true if they're equal (they're both FinchMotorState objects, and all
     * their attributes have the same values), false otherwise.
     */
    @Override
    public synchronized boolean equals(Object fms) {
        // self check
        if (this == fms)
            return true;
        // null check
        if (fms == null)
            return false;
        // type check and cast
        if (getClass() != fms.getClass())
            return false;
        return equals_helper((FinchMotorState) fms);
    }

    /**
     * Copies all attributes of the input FinchMotorState into the current ('this') FinchMotorState.
     *
     * @param source The LedArrayState from which the attributes are copied.
     */
    @Override
    public synchronized void copy(FinchMotorState source) {
        motors.setValue(source.motors.getValues());
        ledArray.setValue(source.ledArray.getCharacters());
    }

    /**
     * Generates a byte array that can be sent to the Robot,
     * to set all the attributes to their current values.
     *
     * @return A byte array containing the required values for all
     * the state objects, in the order shown below.
     */
    @Override
    public synchronized byte[] setAll() {

        byte[] ledArrayBytes = new byte[18];
        boolean symbol = false;
        byte flashLen = 0;
        if (sendLEDArray) {
            int[] lightData = ledArray.getCharacters();
            if (lightData[lightData.length - 1] == 0) {
                // symbol
                symbol = true;
                //all[1] = (byte) 0x80;
                ledArrayBytes[0] = ConstructByteFromInts(lightData, 24, 25);
                ledArrayBytes[1] = ConstructByteFromInts(lightData, 16, 24);
                ledArrayBytes[2] = ConstructByteFromInts(lightData, 8, 16);
                ledArrayBytes[3] = ConstructByteFromInts(lightData, 0, 8);
            } else if (lightData.length == 1 && lightData[0] == -1) {
                //reset
                setSendLEDArray(false);//TODO: ?? is this right
                //all[1] = (byte) 0x00;
                //all[2] = (byte) 0xFF;
                //all[3] = (byte) 0xFF;
                //all[4] = (byte) 0xFF;
            } else {
                // flash
                symbol = false;
                flashLen = (byte) (lightData.length - 1);
                //all[1] = (byte) ((byte) 0x40 | (byte) flashLen);
                for (int i = 0; i < flashLen; i++) {
                    ledArrayBytes[i] = (byte) lightData[i];
                }
            }
        }


        byte[] all = new byte[20];
        all[0] = (byte) 0xD2;

        byte mode = 0;
        if (sendMotors && sendLEDArray){
            if (symbol){
                mode = 0x60;
            } else {
                mode = (byte)(0x80 + flashLen);
            }
        } else if (sendMotors) {
            mode = 0x40;
        } else if (sendLEDArray && symbol){
            mode = 0x20;
        } else if (sendLEDArray) {
            mode = flashLen;
        }
        all[1] = mode;

        if (sendMotors) {
            byte[] motorsBytes = motors.getBytes();
            for (int i=0; i<8; i++){
                all[i+2] = motorsBytes[i];
            }
        }

        if (sendLEDArray){
            if(sendMotors){
                for (int i=0; i<10; i++) {
                    all[i+10] = ledArrayBytes[i];
                }
            } else {
                for (int i=0; i<18; i++) {
                    all[i+2] = ledArrayBytes[i];
                }
            }
        }

        return all;
    }
    private synchronized byte ConstructByteFromInts(int[] data, int start, int end) {
        int resultByte = 0;
        for (int i = start; i < end; i++) {
            resultByte = resultByte + (data[i] << (i - start));
        }
        return (byte) resultByte;
    }

    /**
     * Resets all attributes of all state objects to their default values.
     */
    @Override
    public synchronized void resetAll() {
        motors = new Motors();
        ledArray = new LEDArray();
    }

    public synchronized void resetMotors() {
        motors = new Motors();
    }
}
