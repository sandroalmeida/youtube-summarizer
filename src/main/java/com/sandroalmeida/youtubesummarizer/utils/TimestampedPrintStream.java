package com.sandroalmeida.youtubesummarizer.utils;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * PrintStream wrapper that prefixes each printed line with the current time.
 */
public class TimestampedPrintStream extends PrintStream {
  private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

  public TimestampedPrintStream(PrintStream original) {
    super(original, true);
  }

  @Override
  public void println(String x) {
    super.println(formatMessage(String.valueOf(x)));
  }

  @Override
  public void println(Object x) {
    super.println(formatMessage(String.valueOf(x)));
  }

  @Override
  public void println(boolean x) {
    super.println(formatMessage(String.valueOf(x)));
  }

  @Override
  public void println(char x) {
    super.println(formatMessage(String.valueOf(x)));
  }

  @Override
  public void println(int x) {
    super.println(formatMessage(String.valueOf(x)));
  }

  @Override
  public void println(long x) {
    super.println(formatMessage(String.valueOf(x)));
  }

  @Override
  public void println(float x) {
    super.println(formatMessage(String.valueOf(x)));
  }

  @Override
  public void println(double x) {
    super.println(formatMessage(String.valueOf(x)));
  }

  @Override
  public void println(char[] x) {
    super.println(formatMessage(String.valueOf(x)));
  }

  @Override
  public void println() {
    super.println();
  }

  @Override
  public PrintStream printf(String format, Object... args) {
    return format(format, args);
  }

  @Override
  public PrintStream printf(Locale l, String format, Object... args) {
    return format(l, format, args);
  }

  @Override
  public PrintStream format(String format, Object... args) {
    super.print(formatMessage(String.format(format, args)));
    return this;
  }

  @Override
  public PrintStream format(Locale l, String format, Object... args) {
    super.print(formatMessage(String.format(l, format, args)));
    return this;
  }

  private String formatMessage(String message) {
    String prefix = LocalTime.now().format(TIME_FORMATTER) + " - ";
    return prefix + message;
  }
}
