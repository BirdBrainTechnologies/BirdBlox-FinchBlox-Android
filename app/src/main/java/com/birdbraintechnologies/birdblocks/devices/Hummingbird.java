package com.birdbraintechnologies.birdblocks.devices;

import com.birdbraintechnologies.birdblocks.bluetooth.UARTConnection;

/**
 * Created by tsun on 2/14/17.
 */

public class Hummingbird {
    private UARTConnection conn;

    public Hummingbird(UARTConnection conn) {
        this.conn = conn;
    }

    public boolean setOutput(String outputType, String[] args) {
        int port = Integer.parseInt(args[1]);

        switch (outputType) {
            case "servo":
                return setServo(port, Integer.parseInt(args[2]));
            case "motor":
                return setMotor(port, Integer.parseInt(args[2]));
            case "vibration":
                return setVibrationMotor(port, Integer.parseInt(args[2]));
            case "led":
                return setLED(port, Integer.parseInt(args[2]));
            case "triled":
                return setTriLED(port, Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                        Integer.parseInt(args[4]));
        }

        return false;
    }

    public boolean setServo(int port, int position) {
        return true;
    }

    public boolean setVibrationMotor(int port, int speed) {
        return true;
    }

    public boolean setMotor(int port, int speed) {
        return true;
    }

    public boolean setLED(int port, int intensity) {
        return true;
    }

    public boolean setTriLED(int port, int rPercent, int gPercent, int bPercent) {
        return true;
    }

    public boolean isConnected() {
        return conn.isConnected();
    }

    public void disconnect() {
        conn.disconnect();
    }
}
