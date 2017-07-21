package com.birdbraintechnologies.birdblocks.Robots;

public enum RobotType {

    Hummingbird("Hummingbird"),
    Flutter("Flutter");

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

}
