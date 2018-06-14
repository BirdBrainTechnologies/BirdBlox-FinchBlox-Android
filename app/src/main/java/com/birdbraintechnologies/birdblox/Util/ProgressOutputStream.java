package com.birdbraintechnologies.birdblox.Util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Shreyan Bakshi (AppyFizz)
 *         <p>
 *         SOURCE: https://github.com/dropbox/dropbox-sdk-java/issues/66
 */

public class ProgressOutputStream extends OutputStream {

    private OutputStream underlying;
    private Listener listener;
    private long completed;
    private long totalSize;

    public ProgressOutputStream(long totalSize, OutputStream underlying, Listener listener) {
        this.underlying = underlying;
        this.listener = listener;
        this.completed = 0;
        this.totalSize = totalSize;
    }

    @Override
    public void write(byte[] data, int off, int len) throws IOException {
        this.underlying.write(data, off, len);
        track(len);
    }

    @Override
    public void write(byte[] data) throws IOException {
        this.underlying.write(data);
        track(data.length);
    }

    @Override
    public void write(int c) throws IOException {
        this.underlying.write(c);
        track(1);
    }

    private void track(int len) {
        this.completed += len;
        this.listener.progress(this.completed, this.totalSize);
    }

    public interface Listener {
        public void progress(long completed, long totalSize);
    }

}
