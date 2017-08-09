package com.birdbraintechnologies.birdblox.Sound;

import android.media.MediaPlayer;

import java.io.IOException;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class CancelableMediaPlayer extends MediaPlayer {

    private boolean canceled;

    public CancelableMediaPlayer() {
        super();
        canceled = false;
    }

    public void cancel() {
        canceled = true;
    }

    public boolean isCanceled() {
        return canceled;
    }

    @Override
    public void prepare() throws IOException, IllegalStateException {
        if (!canceled)
            super.prepare();
    }

    @Override
    public void start() throws IllegalStateException {
        if (!canceled) {
            super.start();
        } else {
            this.reset();
            super.release();
        }
    }

    @Override
    public void stop() throws IllegalStateException {
        super.stop();
        // canceled = false;
    }

    @Override
    public void reset() {
        super.reset();
        canceled = false;
    }
}
