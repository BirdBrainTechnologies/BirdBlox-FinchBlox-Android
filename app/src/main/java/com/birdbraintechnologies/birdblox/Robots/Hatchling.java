package com.birdbraintechnologies.birdblox.Robots;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;

import android.util.Log;

import com.birdbraintechnologies.birdblox.Bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.FinchMotorState;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.FinchState;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.HLState;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.RobotStateObject;
import com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects.TriLED;
import com.birdbraintechnologies.birdblox.Util.DeviceUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


/**
 * Represents a Finch device
 */
public class Hatchling extends Robot<HLState, HLState> {


    private static final byte[] TERMINATECOMMAND = new byte[]{(byte) 0xFA, (byte) 6, (byte) 0};

    /**
     * Initializes a Hatchling device
     *
     * @param conn Connection established with the Hatchling device
     */
    public Hatchling(final UARTConnection conn) {
        super(conn, RobotType.Hatchling, false, false);

        //TODO: Do we need these at all? They are being reset at disconnect, but otherwise?
        /*oldPrimaryState = new HLState();
        newPrimaryState = new HLState();
        oldSecondaryState = new HLState();
        newSecondaryState = new HLState();*/

        if (conn.isConnected()) {
            runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(getMacAddress()) + "', true);");
        }
    }

    @Override
    public byte[] getFirmwareCommand() {
        return null;
    }

    @Override
    public byte[] getCalibrateCommand() {
        return null;
    }

    @Override
    public byte[] getResetEncodersCommand() {
        return null;
    }

    @Override
    public byte[] getStartPollCommand() {
        return null;
    }

    @Override
    public byte[] getStopPollCommand() {
        return null;
    }

    @Override
    public byte[] getTerminateCommand() {
        return TERMINATECOMMAND;
    }

    @Override
    public byte[] getStopAllCommand() {
        return getTerminateCommand();
    }

    @Override
    public double[] getBatteryConstantsArray() {
        return new double[]{};
    }

    @Override
    public int getCompassIndex() {
        return 0;
    }

    /**
     * Sets the output of the given output type according to args
     *
     * @param outputType Type of the output
     * @param args       Arguments for setting the output
     * @return True if the output was successfully set, false otherwise
     */
    @Override
    public boolean setOutput(String outputType, Map<String, List<String>> args) {
        // Handle stop output type (since it doesn't have a port specification)
        if (outputType.equals("stop")) {
            return stopAll();
        }

        switch (outputType) {
            case "microblocks":
                String dataString = args.get("data").get(0);
                String[] dataStrings = dataString.split(",");
                byte[] data = new byte[dataStrings.length];
                for (int i = 0; i < dataStrings.length; i++) {
                    data[i] = (byte) Integer.parseInt(dataStrings[i]);
                }
                return sendMicroBlocksData(data);
        }

        return false;
    }

    @Override
    protected void setOutputHelper(RobotStateObject newobj) {}

    /**
     * Reads the value of the sensor at the given port and returns the formatted value according to
     * sensorType
     *
     * @param sensorType Type of sensor connected to the port (dictates format of the returned
     *                   value)
     * @param portString Port that the sensor is connected to
     * @param axisString axis or position requested
     * @return A string representing the value of the sensor
     */
    @Override
    public String readSensor(String sensorType, String portString, String axisString) {
        return "";
    }

    @Override
    protected void sendSecondaryState(int delayInMillis) { }

    @Override
    protected void notifyIncompatible() {
        //Nothing incompatible at this time
    }

    private String getCFResponse(int index) {
        return "";
    }

    @Override
    public String getHardwareVersion() {
        return "";
    }


    @Override
    public boolean hasLatestFirmware() {
        return true;
    }

    @Override
    public boolean hasMinFirmware() {
        return true;
    }

    /*public boolean sendMicroBlocksData(byte[] data) {
        return sendCommand(data);
    }*/

    @Override
    public void onRXData(byte[] newData) {
        Log.d(TAG, "onRXData newdata " + Arrays.toString(newData) );
        String[] dataStrings = new String[newData.length];
        for (int i = 0; i < newData.length; i++) {
            dataStrings[i] = String.valueOf(newData[i] & 0xFF);
        }
        String parameters = String.join(", ", dataStrings);
        runJavascript("CallbackManager.robot.setHLState('" + parameters + "');");

        last_received.set(System.currentTimeMillis());
    }

    @Override
    public boolean stopAll() {
        return true;
    }

    @Override
    public boolean primaryStatesEqual() {
        return true;
    }

}