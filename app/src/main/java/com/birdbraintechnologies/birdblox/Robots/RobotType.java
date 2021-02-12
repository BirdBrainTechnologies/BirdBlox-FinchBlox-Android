package com.birdbraintechnologies.birdblox.Robots;

import android.util.Log;

public enum RobotType {

    Hummingbird("Hummingbird"),
    Hummingbit("Hummingbirdbit"),
    Microbit("Microbit"),
    Finch("Finch");


    private final String name;

    RobotType(String s) {
        name = s;
    }

    public boolean equalsName(String otherName) {
        return name.equals(otherName);
    }

    public String toString() {
        return this.name;
    }

    public static RobotType robotTypeFromString(String robotType) {
        switch (robotType.toLowerCase()) {
        case "hummingbird":
            return RobotType.Hummingbird;
        case "hummingbirdbit":
            return RobotType.Hummingbit;
        case "microbit":
            return RobotType.Microbit;
        case "finch":
            return RobotType.Finch;
        default:
            Log.e("RobotType", "Could not determine RobotType from String " + robotType);
            return null;
        }

    }

    public static RobotType robotTypeFromGAPName(String gapName) {
        switch(gapName.substring(0,2)) {
            case "HM":
            case "HB":
                return RobotType.Hummingbird;
            case "BB":
                return RobotType.Hummingbit;
            case "MB":
                return RobotType.Microbit;
            case "FN":
                return RobotType.Finch;
            default:
                Log.e("RobotType", "Could not determine RobotType from gap name " + gapName);
                return null;
        }
    }

}
