package com.amurcanov.tgwsproxy;

/** Одна строка лога (повторяющиеся подряд сообщения схлопываются счётчиком). */
public final class LogEntry {
    public final String message;
    public int count;
    public final int priority;      // 3=debug, 4=info, 5=warn, 6=error
    public final boolean isError;
    public final boolean isEssential;

    public LogEntry(String message, int count, int priority, boolean isError, boolean isEssential) {
        this.message = message;
        this.count = count;
        this.priority = priority;
        this.isError = isError;
        this.isEssential = isEssential;
    }
}
