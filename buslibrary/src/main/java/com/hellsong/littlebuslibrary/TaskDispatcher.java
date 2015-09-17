package com.hellsong.littlebuslibrary;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by weiruyou on 2015/9/7.
 */
public class TaskDispatcher {
    private static volatile TaskDispatcher sInstance;

    private ExecutorService mExecutorService;

    private TaskDispatcher() {
        mExecutorService = Executors.newCachedThreadPool();
    }

    public static TaskDispatcher getInstance() {
        if (sInstance == null) {
            synchronized (TaskDispatcher.class) {
                if (sInstance == null) {
                    sInstance = new TaskDispatcher();
                }
            }
        }
        return sInstance;
    }

    public void excuteTask(Runnable task, boolean isOnBGThread) {
        if (isOnBGThread) {
            mExecutorService.execute(task);
        } else {
            task.run();
        }
    }

}
