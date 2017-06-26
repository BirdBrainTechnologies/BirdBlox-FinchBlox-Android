package com.birdbraintechnologies.birdblocks.Robots;

import com.birdbraintechnologies.birdblocks.Robots.RobotStates.HBState;
import com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects.RobotStateObject;
import com.birdbraintechnologies.birdblocks.bluetooth.UARTConnection;
import com.birdbraintechnologies.birdblocks.util.DeviceUtil;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a Hummingbird device and all of its functionality: Setting outputs, reading sensors
 *
 * @author Terence Sun (tsun1215)
 * @author Shreyan Bakshi (AppyFizz)
 */
public class Hummingbird extends Robot<HBState> implements UARTConnection.RXDataListener {
    /*
     * Command prefixes for the Hummingbird according to spec
     * More info: http://www.hummingbirdkit.com/learning/hummingbird-duo-usb-protocol
     */
    private static final byte TRI_LED_CMD = 'O';
    private static final byte LED_CMD = 'L';
    private static final byte MOTOR_CMD = 'M';
    private static final byte VIB_MOTOR_CMD = 'V';
    private static final byte SERVO_CMD = 'S';
    private static final byte READ_SENSOR_CMD = 's';
    private static final byte READ_ALL_CMD = 'G';
    private static final byte STOP_PERIPH_CMD = 'X';
    private static final byte TERMINATE_CMD = 'R';
    private static final byte PING_CMD = 'z';
    private static final String RENAME_CMD = "AT+GAPDEVNAME";

    private static final int SETALL_INTERVAL_IN_MILLIS = 1000;
    private static final int COMMAND_TIMEOUT_IN_MILLIS = 5000;
    private static final int SEND_ANYWAY_INTERVAL_IN_MILLIS = 4000;

    private long last_sent;

    private UARTConnection conn;
    private byte[] rawSensorValues;
    private Object rawSensorValuesLock = new Object();

    /**
     * Initializes a Hummingbird device
     *
     * @param conn Connection established with the Hummingbird device
     */
    public Hummingbird(UARTConnection conn) {
        super();
        this.conn = conn;
        oldState = new HBState();
        newState = new HBState();

        last_sent = System.currentTimeMillis();

        new Thread() {
            public void run() {
                new Timer().scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        // TODO: Error if sending fails
                        sendToRobot();
                    }
                }, 0, SETALL_INTERVAL_IN_MILLIS);
            }
        }.start();
    }

    /**
     * Actually sends the commands to the physical Hummingbird,
     * based on certain conditions.
     */
    public synchronized boolean sendToRobot() {
        if (isCurrentlySending()) {
            return true;
        } else if (!statesEqual()) {
            // Not currently sending, but oldState and newState are different
            long currentTime = System.currentTimeMillis();
            // Send here
            setSendingTrue();
            boolean sent = conn.writeBytes(newState.setAll());
            if (sent) oldState.copy(newState);
            setSendingFalse();
            last_sent = currentTime;
            return sent;
        } else {
            // Not currently sending, and oldState and newState are the same
            long currentTime = System.currentTimeMillis();
            if (currentTime - last_sent >= SEND_ANYWAY_INTERVAL_IN_MILLIS) {
                // Send here
                setSendingTrue();
                boolean sent = conn.writeBytes(newState.setAll());
                if (sent) oldState.copy(newState);
                setSendingFalse();
                last_sent = currentTime;
                return sent;
            }
        }
        return false;
    }

    /**
     * Sets the output of the given output type according to args
     *
     * @param outputType Type of the output
     * @param args       Arguments for setting the output
     * @return True if the output was successfully set, false otherwise
     */
    public boolean setOutput(String outputType, Map<String, List<String>> args) {
        // Handle stop output type (since it doesn't have a port specification)
        if (outputType.equals("stop")) {
            return stopAll();
        }

        // All remaining outputs are of the format: /out/<outputType>/<port>/<args>...

        int port = Integer.parseInt(args.get("port").get(0));

        switch (outputType) {
//            case "servo":
//                return setServo(Integer.parseInt(args.get("port").get(0)), Integer.parseInt(args.get("angle").get(0)));
//            case "motor":
//                return setMotor(Integer.parseInt(args.get("port").get(0)), Integer.parseInt(args.get("speed").get(0)));
//            case "vibration":
//                return setVibrationMotor(Integer.parseInt(args.get("port").get(0)), Integer.parseInt(args.get("intensity").get(0)));
//            case "led":
//                return setLED(Integer.parseInt(args.get("port").get(0)), Integer.parseInt(args.get("intensity").get(0)));
//            case "triled":
//                return setTriLED(Integer.parseInt(args.get("port").get(0)), Integer.parseInt(args.get("red").get(0)),
//                        Integer.parseInt(args.get("green").get(0)), Integer.parseInt(args.get("blue").get(0)));

            case "servo":
                return setRbSOOutput(oldState.getServo(port), newState.getServo(port), Integer.parseInt(args.get("angle").get(0)));
            case "motor":
                return setRbSOOutput(oldState.getMotor(port), newState.getMotor(port), Integer.parseInt(args.get("speed").get(0)));
            case "vibration":
                return setRbSOOutput(oldState.getVibrator(port), newState.getVibrator(port),
                        Integer.parseInt(args.get("intensity").get(0)));
            case "led":
                return setRbSOOutput(oldState.getLED(port), newState.getLED(port), Integer.parseInt(args.get("intensity").get(0)));
            case "triled":
                return setRbSOOutput(oldState.getTriLED(port), newState.getTriLED(port), Integer.parseInt(args.get("red").get(0)),
                        Integer.parseInt(args.get("green").get(0)), Integer.parseInt(args.get("blue").get(0)));
        }

        return false;
    }

    /**
     * Reads the value of the sensor at the given port and returns the formatted value according to
     * sensorType
     *
     * @param sensorType Type of sensor connected to the port (dictates format of the returned
     *                   value)
     * @param portString Port that the sensor is connected to
     * @return A string reprsenting the value of the sensor
     */
    public String readSensor(String sensorType, String portString) {
        byte rawSensorValue;
        synchronized (rawSensorValuesLock) {
            if (rawSensorValues == null) {
                rawSensorValues = startPollingSensors();
                conn.addRxDataListener(this);
            }
            int port = Integer.parseInt(portString) - 1;
            rawSensorValue = rawSensorValues[port];
        }

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

    private byte[] startPollingSensors() {
        return conn.writeBytesWithResponse(new byte[]{READ_ALL_CMD, '5'});
    }

    private void stopPollingSensors() {
        conn.writeBytes(new byte[]{READ_ALL_CMD, '6'});
    }

    public boolean setRbSOOutput(RobotStateObject oldobj, RobotStateObject newobj, int... values) {
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try {
            long startTime = System.currentTimeMillis();
            long elapsedTime = 0L;
            while (!newobj.equals(oldobj) && elapsedTime < COMMAND_TIMEOUT_IN_MILLIS) {
                elapsedTime = (new Date()).getTime() - startTime;
            }
            if (newobj.equals(oldobj)) {
                newobj.setValue(values);
                lock.unlock();
                return true;
            }
        } finally {
            lock.unlock();
        }
        return false;
    }


    /**
     * Sets the angle of the servo connected to the given port
     *
     * @param port  Port number that the servo is connected to
     * @param angle Percentage [0,100] to set the intensity to
     * @return True if the command succeeded, false otherwise
     */
    public boolean setServo(int port, int angle) {
        // Compute servo angle [0,225] from angle [0,180]
        byte angleByte = clampToBounds(Math.round(angle * 1.25), 0, 225);
        return conn.writeBytes(new byte[]{SERVO_CMD, computePort(port), angleByte});
        // newState.setServo(port, angleByte);
    }

    /**
     * Sets the intensity of the vibration motor connected to the given port
     *
     * @param port             Port number that the motor is connected to
     * @param intensityPercent
     * @return True if the command succeeded, false otherwise
     */
    public boolean setVibrationMotor(int port, int intensityPercent) {
        // Compute vibration intensity [0,255] from intensityPercentage
        byte intensity = clampToBounds(Math.round(intensityPercent * 2.55), 0, 255);
        return conn.writeBytes(new byte[]{VIB_MOTOR_CMD, computePort(port), intensity});
        // newState.setVibrator(port, intensity);
    }

    /**
     * Sets the speed of the motor connected to the given port
     *
     * @param port         Port number that the motor is connected to
     * @param speedPercent Percentage [-100,100] to set the speed to (a negative value means that
     *                     the motor spins backwards, a positive value means that the motor spins
     *                     forwards)
     * @return True if the command succeeded, false otherwise
     */
    public boolean setMotor(int port, int speedPercent) {
        // Compute direction from speedPercent parity ('0' is forward, '1' is backwards)
        byte direction = (byte) ((speedPercent >= 0) ? '0' : '1');

        // Compute absolute speed [0,255] from speedPercent [-100,100]
        byte speed = clampToBounds(Math.round(Math.abs(speedPercent) * 2.55), 0, 255);
        return conn.writeBytes(new byte[]{MOTOR_CMD, computePort(port), direction, speed});
        // newState.setMotor(port, (speedPercent >= 0 ? speed : (byte) -speed));
    }

    /**
     * Sets the intensity of a LED connected to the given port
     *
     * @param port             Port number that the LED is connected to
     * @param intensityPercent Percentage [0,100] to set the intensity to
     * @return True if the command succeeded, false otherwise
     */
    public boolean setLED(int port, int intensityPercent) {
        // Compute intensity [0,255] from percentage
        byte intensity = clampToBounds(Math.round(intensityPercent * 2.55), 0, 255);
        return conn.writeBytes(new byte[]{LED_CMD, computePort(port), intensity});
        // newState.setLED(port, intensity);
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
    public boolean setTriLED(int port, int rPercent, int gPercent, int bPercent) {
        // Compute rgb values [0,255] from the percentages
        byte r = clampToBounds(Math.round(rPercent * 2.55), 0, 255);
        byte g = clampToBounds(Math.round(gPercent * 2.55), 0, 255);
        byte b = clampToBounds(Math.round(bPercent * 2.55), 0, 255);
        return conn.writeBytes(new byte[]{TRI_LED_CMD, computePort(port), r, g, b});
        // newState.setTriLED(port, r, g, b);
    }

    /**
     * Turns off all LEDs and motors
     *
     * @return True if the command succeeded, false otherwise
     */
    public boolean stopAll() {
        return conn.writeBytes(new byte[]{STOP_PERIPH_CMD});
        // newState.resetAll();
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
        return (byte) ((port - 1) + 48);
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

//    public void rename(String newName) {
//        String cmd = "+++" + "\r" + "\n";
//        Log.d("RENAME", "Request: " + Arrays.toString(cmd.getBytes()));
//        byte[] response = conn.writeBytesWithResponse(cmd.getBytes());
//        Log.d("RENAME", "Response: " + new String(response));
//        try {
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            Log.e("RENAME", e.toString());
//        }
//        cmd = RENAME_CMD + "=";
//        conn.writeBytes(cmd.getBytes());
//        cmd = newName + "\r\n";
//        conn.writeBytes(cmd.getBytes());
//        try {
//            Thread.sleep(500);
//        } catch (InterruptedException e) {
//            Log.e("RENAME", e.toString());
//        }
//        cmd = "ATZ\r\n";
//        conn.writeBytes(cmd.getBytes());
//    }

    /**
     * Disconnects the device
     */
    public void disconnect() {
        conn.removeRxDataListener(this);
        stopPollingSensors();
        conn.writeBytes(new byte[]{TERMINATE_CMD});
        conn.disconnect();
    }

    @Override
    public void onRXData(byte[] newData) {
        synchronized (rawSensorValuesLock) {
            this.rawSensorValues = newData;
        }
    }
}
