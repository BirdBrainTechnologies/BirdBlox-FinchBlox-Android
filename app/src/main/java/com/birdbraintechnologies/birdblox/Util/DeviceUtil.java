package com.birdbraintechnologies.birdblox.Util;

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
    public static double RawToKnob(int raw) {
        return (raw * 100.0 / 230.0) > 100.0 ? 100.0 : (raw * 100.0 / 230.0);
    }

    public static double RawToPercent(byte raw) {
        return RawToInt(raw) / 2.55;
    }

    public static double RawToDistance(int raw) {
        return raw * 1.0;
    }

    /**
     * Converts percent readings [0,100] to raw [0,255]
     */
    public static byte PercentToRaw(double percent) {
        return (byte) (percent * 2.55);
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

    public static double RawToAccl(byte[] rawAccl, String axisString) {
        switch (axisString) {
            case "x":
                return Complement(RawToInt(rawAccl[0])) * 196.0 / 1280.0;
            case "y":
                return Complement(RawToInt(rawAccl[1])) * 196.0 / 1280.0;
            case "z":
                return Complement(RawToInt(rawAccl[2])) * 196.0 / 1280.0;
        }
        return 0.0;
    }

    public static double RawToMag(byte[] rawMag, String axisString) {
        double mx = Complement (rawMag[1] | (rawMag[0] << 8)) * 1.0;
        double my = Complement (rawMag[2] | (rawMag[3] << 8)) * 1.0;
        double mz = Complement (rawMag[4] | (rawMag[5] << 8)) * 1.0;

        switch (axisString) {
            case "x":
                return mx;
            case "y":
                return my;
            case "z":
                return mz;
        }
        return 0.0;
    }

    public static double RawToCompass(byte[] rawAccl, byte[] rawMag) {
        double ax = Complement(RawToInt(rawAccl[0])) * 1.0;
        double ay = Complement(RawToInt(rawAccl[0])) * 1.0;
        double az = Complement(RawToInt(rawAccl[0])) * 1.0;

        double mx = Complement (rawMag[1] | (rawMag[0] << 8)) * 1.0;
        double my = Complement (rawMag[2] | (rawMag[3] << 8)) * 1.0;
        double mz = Complement (rawMag[4] | (rawMag[5] << 8)) * 1.0;

        double phi = Math.atan(-ay / az);
        double theta = Math.atan(ax / (ay * Math.sin(phi) + az * Math.cos(phi)));

        double xp = mx;
        double yp = my * Math.cos(phi) - mz * Math.sin(phi);
        double zp = my * Math.sin(phi) + mz * Math.cos(phi);

        double xpp = xp * Math.cos(theta) + zp * Math.sin(theta);
        double ypp = yp;

        double angle = 180.0 + Math.toDegrees(Math.atan(xpp / ypp));
        return angle;
    }

    public static double RawToSound(int raw) {
        return (raw * 200.0) / 255.0;
    }

    public static double RawToLight(int raw) {
        return (raw * 100.0) / 255.0;
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
    public static int Complement(int prev) {
        if (prev > 127) {
            prev = prev - 256;
        }
        return prev;
    }
}