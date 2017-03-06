package com.birdbraintechnologies.birdblocks.devices;

import android.util.Log;

import com.birdbraintechnologies.birdblocks.bluetooth.UARTConnection;

import java.util.Arrays;

/**
 * Represents a Flutter device and all of its functionality: Setting outputs, reading sensors
 *
 * @author Terence Sun (tsun1215)
 */
public class Flutter implements UARTConnection.RXDataListener {
    private static final String TAG = Flutter.class.getName();
    // TODO: Flutter command constants

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
        // TODO: Implement flutter output functions
        Log.e(TAG, "Call to unimplemented function: setOutput(" + outputType + ", "
                + Arrays.toString(args) + ")");
        return false;
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
        // TODO: Implement flutter sensor reading functions
        Log.e(TAG, "Call to unimplemented function: readSensor(" + sensorType + ", "
                + portString + ")");
        return "";
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
