package com.tracci.module.trip.support;

/**
 * 谁在写行程内容。助手 apply 与手改走的是同一套 ItineraryService，
 * 靠这个标记区分：助手自己写出来的变更不需要再回头同步助手画布。
 */
public final class ItineraryWriteContext {

    public static final String MANUAL = "MANUAL";
    public static final String ASSISTANT = "ASSISTANT";

    private static final ThreadLocal<String> SOURCE = new ThreadLocal<>();

    private ItineraryWriteContext() {
    }

    public static String currentSource() {
        String source = SOURCE.get();
        return source == null ? MANUAL : source;
    }

    public static void runAs(String source, Runnable action) {
        String previous = SOURCE.get();
        SOURCE.set(source);
        try {
            action.run();
        } finally {
            if (previous == null) {
                SOURCE.remove();
            } else {
                SOURCE.set(previous);
            }
        }
    }
}
