package com.birdbraintechnologies.birdblox.Dialogs;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public enum DialogType {
    INPUT, CHOICE;

    public static DialogType fromString(String s) {
        switch (s) {
            case "input":
                return INPUT;
            case "choice":
                return CHOICE;
            default:
                return null;
        }
    }

    @Override
    public String toString() {
        switch (this) {
            case INPUT:
                return "input";
            case CHOICE:
                return "choice";
            default:
                return "";
        }
    }

}
