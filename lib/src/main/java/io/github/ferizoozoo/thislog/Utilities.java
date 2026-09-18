package io.github.ferizoozoo.thislog;

import java.io.PrintStream;

public final class Utilities {

    private Utilities() {
    }

    public static PrintStream logDestinationToPrintStream(LogDestination destination) {
        return switch (destination) {
            case LogDestination.Stdout ignored ->
                System.out;
            case LogDestination.Stderr ignored ->
                System.err;
            case LogDestination.LogFile logFile ->
                FileStreams.acquire(logFile.path());
        };
    }
}
