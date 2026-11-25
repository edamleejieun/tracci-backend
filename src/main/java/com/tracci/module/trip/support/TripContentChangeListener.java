package com.tracci.module.trip.support;

/**
 * 行程内容变更的旁路通知点（在写路径的事务内被调用）。
 * 实现方必须自己异步化，不要阻塞行程写入。
 */
public interface TripContentChangeListener {

    /**
     * @param tripId 发生变化的旅程
     * @param source {@link ItineraryWriteContext#MANUAL} / {@link ItineraryWriteContext#ASSISTANT}
     */
    void onItineraryChanged(String tripId, String source);
}
