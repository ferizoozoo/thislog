package org.example;

public class Formatter implements Formatable {
    private final String format;

    public Formatter(String format) {
        this.format = format;
    }

    @Override
    public String getFormattedString(LogEvent event, Object... args) {
        var formatArgs = new Object[args.length + 2];
        formatArgs[0] = LogLevel.color(event.getLevel());
        formatArgs[1] = event.getMessage();
        System.arraycopy(args, 0, formatArgs, 2, args.length);
        return String.format(format, formatArgs);
    }
}
