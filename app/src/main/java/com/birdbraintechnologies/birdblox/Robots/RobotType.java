package com.birdbraintechnologies.birdblox.Robots;

import androidx.annotation.NonNull;
import android.util.Log;

import com.birdbraintechnologies.birdblox.BuildConfig;

public enum RobotType {

    Hummingbird("Hummingbird", "Duo"),
    Hummingbit("Hummingbirdbit", "Bit"),
    Microbit("Microbit", "micro:bit"),
    Finch("Finch", "Finch"),
    Hatchling("Hatchling", "Hatch");


    private final String name;
    private final String prefix;

    RobotType(String fullName, String shortName) {
        name = fullName;
        prefix = shortName;
    }

    public boolean equalsName(String otherName) {
        return name.equals(otherName);
    }

    @NonNull
    public String toString() {
        return this.name;
    }

    public String getPrefix() { return this.prefix; }

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
        case "hatchling":
            return RobotType.Hatchling;
        default:
            Log.e("RobotType", "Could not determine RobotType from String " + robotType);
            return null;
        }

    }

    public static RobotType robotTypeFromGAPName(String gapName) {
        if (BuildConfig.IS_HATCHLING) {
            return RobotType.Hatchling;
        }

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
