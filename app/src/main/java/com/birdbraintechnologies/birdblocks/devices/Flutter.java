package com.birdbraintechnologies.birdblocks.devices;

import android.util.Log;

import com.birdbraintechnologies.birdblocks.bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblocks.util.DeviceUtil;

import java.util.Arrays;

/**
 * Represents a Flutter device and all of its functionality: Setting outputs, reading sensors
 *
 * @author Terence Sun (tsun1215)
 */
public class Flutter implements UARTConnection.RXDataListener {

    private static final byte SET_CMD = 's';
    private static final byte COMMA = ',';
    private static final byte SERVO_CMD = 's';
    private static final byte TRI_LED_R_CMD = 'r';
    private static final byte TRI_LED_G_CMD = 'g';
    private static final byte TRI_LED_B_CMD = 'b';
    private static final byte READ_CMD = 'r';

    private static final String TAG = Flutter.class.getName();

    private UARTConnection conn;

    /**
     * Initializes a Flutter device
     *
     * @param conn Connection established with the Flutter device
     */
    public Flutter(UARTConnection conn) {
        this.conn = conn;
    }

    /**
     * Sets the output of the given output type according to args
     *
     * @param outputType Type of the output
     * @param args       Arguments for setting the output
     * @return True if the output was successfully set, false otherwise
     */
    public boolean setOutput(String outputType, String[] args) {
        int port = Integer.parseInt(args[1]);
        switch (outputType) {
            case "servo":
                return setServo(port, Integer.parseInt(args[2]));
            case "triled":
                return setTriLED(port, Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                        Integer.parseInt(args[4]));
        }
        return false;
    }

    /**
     * Sets the RGB values of a tri-color LED connected to the given port
     *
     * @param port     Port number that the LED is connected to
     * @param rPercent Percentage [0,100] to set R to
     * @param gPercent Percentage [0,100] to set G to
     * @param bPercent Percentage [0,100] to set B to
     * @return True if the command succeeded, false otherwise
     */
    private boolean setTriLED(int port, int rPercent, int gPercent, int bPercent) {
        Log.v("Flutter", "Setting TriLED");
        byte r = clampToBounds(Math.round(rPercent), 0, 100);
        byte g = clampToBounds(Math.round(gPercent), 0, 100);
        byte b = clampToBounds(Math.round(bPercent), 0, 100);
        boolean check = conn.writeBytes(new byte[]{SET_CMD, TRI_LED_R_CMD, computePort(port), COMMA , r});
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        check &= conn.writeBytes(new byte[]{SET_CMD, TRI_LED_G_CMD, computePort(port), COMMA , g});
        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        check &= conn.writeBytes(new byte[]{SET_CMD, TRI_LED_B_CMD, computePort(port), COMMA , b});
        return check;
    }

    /**
     * Sets the angle of the servo connected to the given port
     *
     * @param port  Port number that the servo is connected to
     * @param angle Percentage [0,100] to set the intensity to
     * @return True if the command succeeded, false otherwise
     */
    private boolean setServo(int port, int angle) {
        byte angleByte = clampToBounds(Math.round(angle * 1.25), 0, 225);
        return conn.writeBytes(new byte[]{SET_CMD, SERVO_CMD, computePort(port), COMMA, angleByte});
    }

    /**
     * Reads the value of the sensor at the given port and returns the formatted value according to
     * sensorType
     *
     * @param sensorType Type of sensor connected to the port (dictates format of the returned
     *                   value)
     * @param portString Port that the sensor is connected to
     * @return A string representing the value of the sensor
     */
    public String readSensor(String sensorType, String portString) {
        byte[] response = conn.writeBytesWithResponse(new byte[]{READ_CMD});
        int port = Integer.parseInt(portString);
        byte rawSensorValue = response[port];

        switch (sensorType) {
            case "distance":
                return Double.toString(DeviceUtil.RawToDist(rawSensorValue));
            case "temperature":
                return Double.toString(DeviceUtil.RawToTemp(rawSensorValue));
            case "sound":
            case "light":
            case "sensor":
            default:
                return Double.toString(DeviceUtil.RawToPercent(rawSensorValue));
        }
    }

    /**
     * Computes the ascii byte of the port number
     *
     * @param port Integer port to convert
     * @return Ascii representation of the port
     */
    private byte computePort(int port) {
        // TODO: Error handling for invalid ports
        // Adding 48 to a number 0-9 makes it ascii
        return (byte) ((port) + 48);
    }

    /**
     * Returns a value that is bounded by min and max
     *
     * @param value Value to be clamped
     * @param min   Minimum that this value can be
     * @param max   Maximum that this value can be
     * @return Clamped value
     */
    private byte clampToBounds(long value, int min, int max) {
        if (value > max) {
            return (byte) max;
        } else if (value < min) {
            return (byte) min;
        } else {
            return (byte) value;
        }
    }

    /**
     * Returns whether or not this device is connected
     *
     * @return True if connected, false otherwise
     */
    public boolean isConnected() {
        return conn.isConnected();
    }

    public void rename(String newName) {
        // TODO: Implement renaming
        Log.e(TAG, "Call to unimplemented function: rename(" + newName + ")");
    }

    /**
     * Disconnects the device
     */
    public void disconnect() {
        conn.removeRxDataListener(this);
        conn.disconnect();
    }

    @Override
    public void onRXData(byte[] newData) {
        // TODO: Implement listener for getting new RX data
    }
}
