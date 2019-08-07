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
            Log.d("RobotType", "About to return null");
            return null;
        }

    }

}
