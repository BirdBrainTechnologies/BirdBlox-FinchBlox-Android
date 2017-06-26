package com.birdbraintechnologies.birdblocks.Robots.RobotStates.RobotStateObjects;

import android.util.Log;

import java.util.Arrays;

/**
 * @author Shreyan Bakshi (AppyFizz).
 */

public class TriLED extends RobotStateObject{

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

    public synchronized byte[] getRGB() {
        byte[] rgb = new byte[3];
        rgb[0] = red;
        rgb[1] = green;
        rgb[2] = blue;
        return rgb;
    }

    private synchronized void setRGB(byte r, byte g, byte b) {
        red = r;
        green = g;
        blue = b;
    }

    private synchronized void setRGB(int r, int g, int b) {
        red = clampToBounds(Math.round(r * 2.55), 0, 255);
        green = clampToBounds(Math.round(g * 2.55), 0, 255);
        blue = clampToBounds(Math.round(b * 2.55), 0, 255);
    }

    private synchronized void setRGB(byte[] rgb) {
        try {
            red = rgb[0];
            green = rgb[1];
            blue = rgb[2];
        } catch (ArrayIndexOutOfBoundsException | ArrayStoreException | NegativeArraySizeException e) {
            Log.e("TriLED", "setRGB: " + e.getMessage());
        }
    }

    private synchronized void setRGB(int[] rgb) {
        try {
            red = clampToBounds(Math.round(rgb[0] * 2.55), 0, 255);
            green = clampToBounds(Math.round(rgb[1] * 2.55), 0, 255);
            blue = clampToBounds(Math.round(rgb[2] * 2.55), 0, 255);
        } catch (ArrayIndexOutOfBoundsException | ArrayStoreException | NegativeArraySizeException e) {
            Log.e("TriLED", "setRGB: " + e.getMessage());
        }
    }

    @Override
    public void setValue(int...values) {
        if (values.length == 3) {
            setRGB(values[0], values[1], values[2]);
        }
    }

    @Override
    public void setValue(byte...values) {
        if (values.length == 3) {
            setRGB(values);
        }
    }

    @Override
    public boolean equals(Object triled) {
        // self check
        if (this == triled)
            return true;
        // null check
        if (triled == null)
            return false;
        // type check and cast
        if (getClass() != triled.getClass())
            return false;
        return Arrays.equals(this.getRGB(), ((TriLED) triled).getRGB());
    }

}
