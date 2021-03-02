package com.birdbraintechnologies.birdblox.Robots.RobotStates.RobotStateObjects;

import android.util.Log;

import java.util.Arrays;

public class HBitBuzzer extends RobotStateObject {
    private short frequency;
    private short duration;

    public HBitBuzzer() {
        short stopFrequency = 0;
        short stopDuration = 1;
        duration = stopDuration;
        frequency = stopFrequency;
    }

    public HBitBuzzer(short f, short d) {
        duration = d;
        frequency = f;
    }

    public short getDuration() {
        return duration;
    }

    public short getFrequency() {
        return frequency;
    }

    public short[] getFD() {
        short[] fd = new short[2];
        fd[0] = frequency;
        fd[1] = duration;
        return fd;
    }

    private void setFD(short f, short d) {
        duration = d;
        frequency = f;
    }

    private void setFD(byte f, byte d) {
        duration = (short) d;
        frequency = (short) f;
    }

    @Override
    public void setValue(int... values) {
        if (values.length == 2) {
            if (values[0] == 0 && values[1] == 0) {
                setFD((short) 0, (short) 0);
            } else {
                short noteToHertz = (short) (1000.0 / ((Math.pow(2.0, ((short) values[0] * 1.0 - 69.0) / 12.0) * 440.0)) * 1000.0);
                setFD(noteToHertz, (short) values[1]);
            }
        } else {
            Log.e("HBitBuzzer", "setValue incorrect arg number " + values.length);
        }
    }

    @Override
    public void setValue(byte... values) {
        if (values.length == 2) {
            setFD(values[0], values[1]);
        }
    }

    @Override
    public boolean equals(Object buzzer) {
        // self check
        if (this == buzzer)
            return true;
        // null check
        if (buzzer == null)
            return false;
        // type check and cast
        if (getClass() != buzzer.getClass())
            return false;
        return Arrays.equals(this.getFD(), ((HBitBuzzer) buzzer).getFD());
    }
}
