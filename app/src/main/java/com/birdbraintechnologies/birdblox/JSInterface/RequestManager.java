package com.birdbraintechnologies.birdblox.JSInterface;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RequestManager {
    private static final String TAG = RequestManager.class.getSimpleName();
    private static final RequestManager sInstance;
    /*
     * Gets the number of available cores
     * (not always the same as the maximum number of cores)
     */
    private static int NUMBER_OF_CORES =
            Runtime.getRuntime().availableProcessors();
    // Sets the amount of time an idle thread waits before terminating
    private static final int KEEP_ALIVE_TIME = 1;
    // Sets the Time Unit to seconds
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;
    // A queue of Runnables
    private final BlockingQueue<Runnable> requestWorkQueue;
    private final ThreadPoolExecutor requestThreadPool;


    static  {
        // Creates a single static instance of PhotoManager
        sInstance = new RequestManager();
    }

    private RequestManager() {
        // Instantiates the queue of Runnables as a LinkedBlockingQueue
        requestWorkQueue = new LinkedBlockingQueue<Runnable>();
        // Creates a thread pool manager
        requestThreadPool = new ThreadPoolExecutor(
                NUMBER_OF_CORES/2,       // Initial pool size TODO:find right size
                NUMBER_OF_CORES,       // Max pool size
                KEEP_ALIVE_TIME,
                KEEP_ALIVE_TIME_UNIT,
                requestWorkQueue);

    }

    // Called by the JavascriptInterface to route a request
    static public void handleRequest (RequestRunnable runnable){

        // Adds a request to the thread pool for execution
        sInstance.requestThreadPool.execute(runnable);

    }

}
