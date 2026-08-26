package org.example;

public interface Formatable {
    public static final String DEFAULT_FORMAT = "%s%s" + LogLevel.color(LogLevel.RESET);

    String getFormattedString(LogEvent event, Object... args);
}
