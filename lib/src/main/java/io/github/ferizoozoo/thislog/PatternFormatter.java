package io.github.ferizoozoo.thislog;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class PatternFormatter implements LogFormatter {
    public static final String DEFAULT_PATTERN = "%s";
    public static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern(DEFAULT_DATE_PATTERN)
            .withZone(ZoneId.systemDefault());

    private static final Map<String, Function<LogEvent, String>> CONVERSIONS = new LinkedHashMap<>();
    static {
        CONVERSIONS.put("%message", LogEvent::getMessage);
        CONVERSIONS.put("%logger", LogEvent::getLoggerName);
        CONVERSIONS.put("%thread", LogEvent::getThreadName);
        CONVERSIONS.put("%level", event -> String.valueOf(event.getLevel()));
        CONVERSIONS.put("%date", event -> CLOCK.format(Instant.ofEpochMilli(event.getTimestamp())));
        CONVERSIONS.put("%msg", LogEvent::getMessage);
        CONVERSIONS.put("%m", LogEvent::getMessage);
        CONVERSIONS.put("%s", LogEvent::getMessage);
        CONVERSIONS.put("%n", event -> System.lineSeparator());
    }

    private final String pattern;
    private final List<Function<LogEvent, String>> parts;

    private PatternFormatter(String pattern) {
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.parts = compile(pattern);
    }

    public static PatternFormatter create(String pattern) {
        return new PatternFormatter(pattern);
    }

    @Override
    public String format(LogEvent event) {
        var line = new StringBuilder();
        for (var part : this.parts) {
            line.append(part.apply(event));
        }
        return line.toString();
    }

    public String pattern() {
        return this.pattern;
    }

    private static List<Function<LogEvent, String>> compile(String pattern) {
        var parts = new ArrayList<Function<LogEvent, String>>();
        var literal = new StringBuilder();

        int at = 0;
        while (at < pattern.length()) {
            var name = conversionAt(pattern, at);
            if (name == null) {
                literal.append(pattern.charAt(at));
                at++;
                continue;
            }
            if (!literal.isEmpty()) {
                var text = literal.toString();
                parts.add(event -> text);
                literal.setLength(0);
            }
            parts.add(CONVERSIONS.get(name));
            at += name.length();
        }

        if (!literal.isEmpty()) {
            var text = literal.toString();
            parts.add(event -> text);
        }
        return parts;
    }

    private static String conversionAt(String pattern, int at) {
        for (var name : CONVERSIONS.keySet()) {
            if (pattern.startsWith(name, at) && endsHere(pattern, at + name.length())) {
                return name;
            }
        }
        return null;
    }

    private static boolean endsHere(String pattern, int after) {
        return after >= pattern.length() || !Character.isLetter(pattern.charAt(after));
    }
}
