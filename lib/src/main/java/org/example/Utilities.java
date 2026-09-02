package org.example;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;

public class Utilities {
    public static PrintStream LogDestinationToPrintStream(LogDestination destination) {
        return switch (destination) {
            case LogDestination.Stdout ignored ->
                System.out;
            case LogDestination.Stderr ignored ->
                System.err;
            case LogDestination.LogFile logFile -> {
                try {
                    yield new PrintStream(
                            new BufferedOutputStream(new FileOutputStream(logFile.path(), true)),
                            false);
                } catch (FileNotFoundException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }
}
