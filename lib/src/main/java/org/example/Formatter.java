package org.example;

import java.time.format.DateTimeFormatter;

public class Formatter implements Formatable {
    public static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public String getFormattedString(String format, Object... args) {
        return String.format(format, args);
    }
}
