package com.birdbraintechnologies.birdblox.Sound;

import android.media.MediaPlayer;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;

/**
 * @author Shreyan Bakshi (AppyFizz)
 */

public class CancelableMediaPlayer extends MediaPlayer {
    private String TAG = this.getClass().getSimpleName();
    private boolean canceled;

    public CancelableMediaPlayer() {
        super();
        canceled = false;
        this.setOnErrorListener(new MediaPlayer.OnErrorListener() {

            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                Log.e(TAG, what + ", " + extra);
                mp.reset();

                //True if the method handled the error, false if it didn't. Returning false, or not having an OnErrorListener at all, will cause the OnCompletionListener to be called.
                return true;
            }
        });
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
