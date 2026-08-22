package org.example;

public interface Formatable {
    public static final String DEFAULT_FORMAT = "%s[%s] %s%s";

    String getFormattedString(String format, Object... args);
}
