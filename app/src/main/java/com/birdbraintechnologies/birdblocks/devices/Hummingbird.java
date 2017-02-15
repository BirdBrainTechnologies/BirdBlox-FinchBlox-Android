package com.birdbraintechnologies.birdblocks.devices;

import com.birdbraintechnologies.birdblocks.bluetooth.UARTConnection;

/**
 * Created by tsun on 2/14/17.
 */

public class Hummingbird {
    private static final byte TRI_LED_CMD = 'O';
    private static final byte LED_CMD = 'L';
    private static final byte MOTOR_CMD = 'M';
    private static final byte VIB_MOTOR_CMD = 'V';
    private static final byte SERVO_CMD = 'S';
    private static final byte READ_PORT_CMD = 's';
    private static final byte STATE_CMD = 'G';
    private static final byte STOP_PERIPH_CMD = 'X';
    private static final byte TERMINATE_CMD = 'R';
    private static final byte PING_CMD = 'z';
    private UARTConnection conn;

    public Hummingbird(UARTConnection conn) {
        this.conn = conn;
    }

    public boolean setOutput(String outputType, String[] args) {
        // Handle stop output type (since it doesn't have a port specification)
        if (outputType.equals("stop")) {
            return stopAll();
        }

        // All remaining outputs are of the format: /out/<outputType>/<port>/<args>...
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

    public boolean setServo(int port, int angle) {
        // Compute servo angle [0,225] from angle [0,180]
        byte angleByte = clampToBounds(Math.round(angle * 1.25), 0, 225);
        return conn.writeBytes(new byte[]{SERVO_CMD, computePort(port), angleByte});
    }

    public boolean setVibrationMotor(int port, int intensityPercent) {
        // Compute vibration intensity [0,255] from intensityPercentage
        byte intensity = clampToBounds(Math.round(intensityPercent * 2.55), 0, 255);
        return conn.writeBytes(new byte[]{VIB_MOTOR_CMD, computePort(port), intensity});
    }

    public boolean setMotor(int port, int speedPercent) {
        // Compute direction from speedPercent parity ('0' is forward, '1' is backwards)
        byte direction = (byte) ((speedPercent >= 0) ? '0' : '1');

        // Compute absolute speed [0,255] from speedPercent [-100,100]
        byte speed = clampToBounds(Math.round(Math.abs(speedPercent) * 2.55), 0, 255);
        return conn.writeBytes(new byte[]{MOTOR_CMD, computePort(port), direction, speed});
    }

    public boolean setLED(int port, int intensityPercent) {
        // Compute intensity [0,255] from percentage
        byte intensity = clampToBounds(Math.round(intensityPercent * 2.55), 0, 255);
        return conn.writeBytes(new byte[]{LED_CMD, computePort(port), intensity});
    }

    public boolean setTriLED(int port, int rPercent, int gPercent, int bPercent) {
        // Compute rgb values [0,255] from the percentages
        byte r = clampToBounds(Math.round(rPercent * 2.55), 0, 255);
        byte g = clampToBounds(Math.round(gPercent * 2.55), 0, 255);
        byte b = clampToBounds(Math.round(bPercent * 2.55), 0, 255);

        return conn.writeBytes(new byte[]{TRI_LED_CMD, computePort(port), r, g, b});
    }

    public boolean stopAll() {
        return conn.writeBytes(new byte[]{STOP_PERIPH_CMD});
    }

    private byte computePort(int port) {
        // Adding 48 to a number 0-9 makes it ascii
        return (byte) ((port - 1) + 48);
    }

    private byte clampToBounds(long value, int min, int max) {
        if (value > max) {
            return (byte) max;
        } else if (value < min) {
            return (byte) min;
        } else {
            return (byte) value;
        }
    }

    public boolean isConnected() {
        return conn.isConnected();
    }

    public void disconnect() {
        conn.writeBytes(new byte[]{TERMINATE_CMD});
        conn.disconnect();
    }
}
