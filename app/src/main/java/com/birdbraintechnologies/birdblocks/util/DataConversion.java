package com.birdbraintechnologies.birdblocks.util;

/**
 * Created by Brandon on 6/24/2015.
 */
public class DataConversion {

    public static int rawTo100Scale(byte raw) {
        return (int) (Math.floor(((double) (raw & 0xff)) / 2.55));
    }

    public static int rawToTemp(byte raw) {
        return (int) Math.floor(((((double) (raw & 0xff)) - 127.0) / 2.4 + 25) * 100 / 100);
    }

    public static int rawToDistance(byte raw) {
        double reading = ((double) (raw & 0xff)) * 4.0;
        if (reading < 130) {
            return 100;
        } else {//formula based on mathematical regression
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
                return (int) distance;
            }
        }
    }

    public static int rawToVoltage(byte raw) {
        return (int) (Math.floor((100.0 * ((double) (raw & 0xff)) / 51.0) / 100));
    }

    public static int rawToSound(byte raw) {
        return (raw & 0xff);
    }

    public static int rawToRotary(byte raw) {
        return rawTo100Scale(raw);
    }

    public static int rawToLight(byte raw) {
        return rawTo100Scale(raw);
    }

    public static int rawToInt(byte raw) {
        return raw & 0xff;
    }
}