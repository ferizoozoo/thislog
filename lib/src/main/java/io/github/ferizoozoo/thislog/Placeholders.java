package io.github.ferizoozoo.thislog;

import java.util.Arrays;

final class Placeholders {

    private Placeholders() {
    }

    static String formatMessageWithParams(String message, Object[] params) {
        if (message == null || params == null || params.length == 0) {
            return message;
        }

        StringBuilder sb = new StringBuilder();
        int paramIndex = 0;
        int lastIndex = 0;

        for (int i = 0; i < message.length(); i++) {
            if (message.charAt(i) == '{' && i + 1 < message.length() && message.charAt(i + 1) == '}') {
                sb.append(message, lastIndex, i);
                if (paramIndex < params.length) {
                    Object param = params[paramIndex++];
                    if (param != null && param.getClass().isArray()) {
                        String rendered = Arrays.deepToString(new Object[] { param });
                        sb.append(rendered, 1, rendered.length() - 1);
                    } else {
                        sb.append(param);
                    }
                } else {
                    sb.append("{}");
                }
                lastIndex = i + 2;
                i++;
            }
        }

        sb.append(message, lastIndex, message.length());
        return sb.toString();
    }

    static Throwable trailingThrowable(Object[] params) {
        if (params == null || params.length == 0) {
            return null;
        }
        return params[params.length - 1] instanceof Throwable thrown ? thrown : null;
    }

    static boolean placeholdersLessThanArgumentList(String message, Object[] params) {
        if (message == null || params == null) {
            return false;
        }

        int count = 0;
        for (int i = 0; i < message.length() - 1; i++) {
            if (message.charAt(i) == '{' && message.charAt(i + 1) == '}') {
                count++;
            }
        }

        return count < params.length;
    }
}
