package com.birdbraintechnologies.birdblocks.util;

/**
 * Utility class for the Hummingbird. Contains library functions for converting from raw sensor
 * values to different units.
 *
 * @author Brandon Price
 * @author Terence Sun (tsun1215)
 */
public class DeviceUtil {

    /**
     * Converts raw readings from sensors [0,255] into percentage [0,100]
     *
     * @param raw Raw reading from sensor
     * @return Sensor reading as a percentage
     */
    public static double RawToPercent(byte raw) {
        return RawToInt(raw) / 2.55;
    }

    /**
     * Converts raw readings from sensors [0,255] into temperature
     *
     * @param raw Raw reading from sensor
     * @return Sensor reading as temperature
     */
    public static double RawToTemp(byte raw) {
        return ((RawToInt(raw) - 127.0) / 2.4 + 25) * 100 / 100;
    }

    /**
     * Converts raw readings from sensors [0,255] into distance
     *
     * @param raw Raw reading from sensor
     * @return Sensor reading as distance
     */
    public static double RawToDist(byte raw) {
        double reading = RawToInt(raw) * 4.0;
        if (reading < 130) {
            return 100;
        } else {
            // Formula based on mathematical regression
            reading = reading - 120.0;
            if (reading > 680.0) {
                return 5;
            } else {
                double sensor_val_square = reading * reading;
                double distance = sensor_val_square * sensor_val_square * reading * -0.000000000004789 +
                        sensor_val_square * sensor_val_square * 0.000000010057143 -
                        sensor_val_square * reading * 0.000008279033021 +
                        sensor_val_square * 0.003416264518201 -
                        reading * 0.756893112198934 +
                        90.707167605683000;
                return distance;
            }
        }
    }

    /**
     * Converts raw readings from sensors [0,255] into voltage
     *
     * @param raw Raw reading from sensor
     * @return Sensor reading as voltage
     */
    public static double RawToVoltage(byte raw) {
        return (100.0 * RawToInt(raw) / 51.0) / 100;
    }

    /**
     * Converts raw readings from bytes (always signed), into an unsigned int value
     *
     * @param raw Reading from sensor
     * @return Sensor value represented as an int [0,255]
     */
    public static int RawToInt(byte raw) {
        return raw & 0xff;
    }
}