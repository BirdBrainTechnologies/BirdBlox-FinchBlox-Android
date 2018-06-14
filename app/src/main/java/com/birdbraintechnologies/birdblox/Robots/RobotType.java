package com.birdbraintechnologies.birdblox.Robots;

import android.util.Log;

public enum RobotType {

    Hummingbird("Hummingbird"),
    Hummingbit("Hummingbirdbit"),
    Microbit("Microbit");


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
        if (robotType.toLowerCase().equals("hummingbird")) {
            return RobotType.Hummingbird;
        } else if (robotType.toLowerCase().equals("hummingbirdbit")) {
            return RobotType.Hummingbit;
        } else if (robotType.toLowerCase().equals("microbit")) {
            return RobotType.Microbit;
        }

        Log.d("RobotType", "About to return null");
        return null;
    }

}
