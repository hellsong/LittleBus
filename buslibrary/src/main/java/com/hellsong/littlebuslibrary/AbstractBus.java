package com.hellsong.littlebuslibrary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class AbstractBus {
    public static final String BusClassName = "LittleBus";
    public static final String BusPackageName = "com.helsong.littlebuslibrary";

    protected Map<Class<?>, Set<Object>> mEventHandlerMap;
    protected TaskDispatcher mDispatcher;

    public AbstractBus() {
        mDispatcher = TaskDispatcher.getInstance();
        mEventHandlerMap = new HashMap<>();
    }

    protected static Set<Class<?>> getHierarchyTypes(Object event) {
        Set<Class<?>> set = new HashSet<>();
        Class<?> eventClass = event.getClass();
        set.add(eventClass);
        eventClass = eventClass.getSuperclass();
        while (eventClass != null && eventClass != Object.class) {
            set.add(eventClass);
            eventClass = eventClass.getSuperclass();
        }
        return set;
    }
}
