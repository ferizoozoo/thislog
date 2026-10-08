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
import java.util.regex.Pattern;

public class PatternFormatter implements LogFormatter {

    public static final String DEFAULT_PATTERN = "%s";

    public static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";

    private static final Map<String, Function<LogEvent, String>> CONVERSIONS = new LinkedHashMap<>();
    static {
        CONVERSIONS.put("message", LogEvent::getMessage);
        CONVERSIONS.put("logger", LogEvent::getLoggerName);
        CONVERSIONS.put("thread", LogEvent::getThreadName);
        CONVERSIONS.put("level", event -> String.valueOf(event.getLevel()));
        CONVERSIONS.put("date", date(DEFAULT_DATE_PATTERN));
        CONVERSIONS.put("msg", LogEvent::getMessage);
        CONVERSIONS.put("m", LogEvent::getMessage);
        CONVERSIONS.put("s", LogEvent::getMessage);
        CONVERSIONS.put("n", event -> System.lineSeparator());
        CONVERSIONS.put("ex", PatternFormatter::stackTrace);
        CONVERSIONS.put("nopex", event -> "");
    }

    private static final Pattern WIDTH = Pattern.compile("(-?\\d+)?(\\.\\d+)?");

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

    @Override
    public boolean rendersThrown() {
        return this.parts.contains(CONVERSIONS.get("ex")) || this.parts.contains(CONVERSIONS.get("nopex"));
    }

    public String pattern() {
        return this.pattern;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PatternFormatter that && this.pattern.equals(that.pattern);
    }

    @Override
    public int hashCode() {
        return this.pattern.hashCode();
    }

    private static List<Function<LogEvent, String>> compile(String pattern) {
        var parts = new ArrayList<Function<LogEvent, String>>();
        var literal = new StringBuilder();

        int at = 0;
        while (at < pattern.length()) {
            var width = widthAt(pattern, at + 1);
            var name = pattern.charAt(at) == '%' ? conversionAt(pattern, at + 1 + width.length()) : null;

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
            at += 1 + width.length() + name.length();

            var part = CONVERSIONS.get(name);
            if (name.equals("date") && pattern.startsWith("{", at) && pattern.indexOf('}', at) > 0) {
                var close = pattern.indexOf('}', at);
                part = date(pattern.substring(at + 1, close));
                at = close + 1;
            }
            parts.add(width.isEmpty() ? part : withWidth(part, width));
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

    private static String widthAt(String pattern, int at) {
        var width = WIDTH.matcher(pattern).region(Math.min(at, pattern.length()), pattern.length());
        return width.lookingAt() ? width.group() : "";
    }

    private static Function<LogEvent, String> withWidth(Function<LogEvent, String> part, String width) {
        var spec = "%" + width + "s";
        return event -> String.format(spec, part.apply(event));
    }

    private static Function<LogEvent, String> date(String format) {
        var clock = DateTimeFormatter.ofPattern(format).withZone(ZoneId.systemDefault());
        return event -> clock.format(Instant.ofEpochMilli(event.getTimestamp()));
    }

    private static String stackTrace(LogEvent event) {
        var thrown = event.getThrown();
        return thrown == null ? "" : System.lineSeparator() + StackTraces.render(thrown);
    }

}
