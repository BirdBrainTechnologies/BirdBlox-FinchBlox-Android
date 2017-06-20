package com.birdbraintechnologies.birdblocks.States.StateObjects;

import android.util.Log;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public class TriLED {

    private byte red;
    private byte green;
    private byte blue;

    public TriLED() {
        red = 0;
        green = 0;
        blue = 0;
    }

    public TriLED(byte r, byte g, byte b) {
        red = r;
        green = g;
        blue = b;
    }

    public byte getRed() {
        return red;
    }

    public void setRed(byte r) {
        red = r;
    }

    public byte getGreen() {
        return green;
    }

    public void setGreen(byte g) {
        green = g;
    }

    public byte getBlue() {
        return blue;
    }

    public void setBlue(byte b) {
        blue = b;
    }

    public byte[] getRGB() {
        byte[] rgb = new byte[3];
        rgb[0] = red;
        rgb[1] = green;
        rgb[2] = blue;
        return rgb;
    }


    public void setRGB(byte r, byte g, byte b) {
        red = r;
        blue = b;
        green = g;
    }

    public void setRGB(byte[] rgb) {
        try {
            red = rgb[0];
            blue = rgb[1];
            green = rgb[2];
        } catch (ArrayIndexOutOfBoundsException | ArrayStoreException | NegativeArraySizeException e) {
            Log.e("TriLED", "setRGB: " + e.getMessage());
        }

    }

}
