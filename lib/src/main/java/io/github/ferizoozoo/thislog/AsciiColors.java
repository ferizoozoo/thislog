package io.github.ferizoozoo.thislog;

public enum AsciiColors {
    RESET("\u001B[0m"),
    GREEN("\u001B[32m"),
    YELLOW("\u001B[33m"),
    RED("\u001B[31m"),
    BLUE("\u001B[34m");

    private final String color;

    AsciiColors(String color) {
        this.color = color;
    }

    public static String color(LogLevel level) {
        return switch (level) {
            case DEBUG ->
                BLUE.toString();
            case INFO ->
                GREEN.toString();
            case WARN ->
                YELLOW.toString();
            case ERROR ->
                RED.toString();
            case TRACE ->
                BLUE.toString();
            case FATAL ->
                RED.toString();
        };
    }

    public static String coloredMessage(String message, LogLevel level) {
        return color(level) + message + reset();
    }

    private static String reset() {
        return RESET.toString();
    }

    @Override
    public String toString() {
        return this.color;
    }
}
