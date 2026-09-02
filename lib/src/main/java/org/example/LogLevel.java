package org.example;

public enum LogLevel {
    TRACE(10), DEBUG(20), INFO(30), WARN(40), ERROR(50), FATAL(60), OFF(Integer.MAX_VALUE);

    private final int severity;

    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String BLUE = "\u001B[34m";

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return this.severity;
    }

    public static String color(LogLevel level) {
        return switch (level) {
            case DEBUG ->
                BLUE;
            case INFO ->
                GREEN;
            case WARN ->
                YELLOW;
            case ERROR ->
                RED;
            case TRACE ->
                BLUE;
            case FATAL ->
                RED;
            case OFF ->
                RESET;
        };
    }
}
