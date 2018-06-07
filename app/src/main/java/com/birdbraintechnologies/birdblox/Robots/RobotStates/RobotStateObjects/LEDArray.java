package com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects;

import java.util.Arrays;

public class LEDArray extends RobotStateObject{

    private int[] characters;

    public LEDArray() {
        characters = new int[1];
    }

    public LEDArray(int[] asciis) {
        int size = asciis.length;
        characters = new int[size];
        for (int i = 0; i < size; i++) {
            characters[i] = asciis[i];
        }
    }

    public int[] getCharacters() {
        return characters;
    }


    private void setCharacters(int[] asciis) {
        int size = asciis.length;
        characters = new int[size];
        for (int i = 0; i < size; i++) {
            characters[i] = asciis[i];
        }
    }

    private void setCharacters(byte[] asciis) {
        int size = asciis.length;
        characters = new int[size];
        for (int i = 0; i < size; i++) {
            characters[i] = (int) asciis[i];
        }
    }



    @Override
    public void setValue(int... values) {
        setCharacters(values);
    }

    @Override
    public void setValue(byte... values) {
        setCharacters(values);
    }

    @Override
    public boolean equals(Object ledArray) {
        // self check
        if (this == ledArray)
            return true;
        // null check
        if (ledArray == null)
            return false;
        // type check and cast
        if (getClass() != ledArray.getClass())
            return false;
        return Arrays.equals(this.getCharacters(), ((LEDArray) ledArray).getCharacters());
    }
}
