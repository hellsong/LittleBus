package com.hellsong.processor;

import java.util.HashMap;

/**
 * Created by weiruyou on 2015/9/9.
 */
public class SubscriberClassInfo {
    private HashMap<String, EventHandlerMethod> mEventMap;
    private String mSubcriberClassName;

    public SubscriberClassInfo() {
        mEventMap = new HashMap<>();
    }

    public HashMap<String, EventHandlerMethod> getEventMap() {
        return mEventMap;
    }

    public void addEvent(String eventFullName, EventHandlerMethod method) {
        mEventMap.put(eventFullName, method);
    }

    public String getSubscriberClass() {
        return mSubcriberClassName;
    }

    public void setSubcriberClassName(String mSubcriberClassName) {
        this.mSubcriberClassName = mSubcriberClassName;
    }

    public static class EventHandlerMethod {
        public String mMethod;
        public boolean isRunOnMainThread;
    }
}
