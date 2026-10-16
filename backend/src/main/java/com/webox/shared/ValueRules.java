package com.webox.shared;

import java.util.Locale;
import java.util.regex.Pattern;

/** Shared scalar rules. No floating point is used for money. */
public final class ValueRules {
    public static final long MAX_SAFE_INTEGER = 9007199254740991L;
    private static final Pattern EDGE_SPACE = Pattern.compile("^[\\x09-\\x0D\\x20\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+|[\\x09-\\x0D\\x20\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+$");
    private ValueRules() {}
    public static String uuid(String value) {
        if(value==null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw new IllegalArgumentException("Invalid identifier.");
        return java.util.UUID.fromString(value).toString();
    }
    public static java.time.LocalDate date(String value) {
        if(value==null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("Invalid date.");
        try { return java.time.LocalDate.parse(value); }
        catch(java.time.format.DateTimeParseException e) { throw new IllegalArgumentException("Invalid date."); }
    }
    public static long safeInteger(long value) {
        if (value < 0 || value > MAX_SAFE_INTEGER) throw new IllegalArgumentException("Value must be a non-negative safe integer.");
        return value;
    }
    public static long add(long a, long b) {
        safeInteger(a); safeInteger(b);
        if (a > MAX_SAFE_INTEGER - b) throw new IllegalArgumentException("Total exceeds the supported amount.");
        return a + b;
    }
    public static long multiply(long a, long b) {
        safeInteger(a); safeInteger(b);
        if (b != 0 && a > MAX_SAFE_INTEGER / b) throw new IllegalArgumentException("Total exceeds the supported amount.");
        return a * b;
    }
    public static long nextVersion(long value) { return add(value, 1); }
    public static String money(long cents) {
        safeInteger(cents);
        return String.format(Locale.ROOT, "%d.%02d", cents / 100, cents % 100);
    }
    public static String requiredText(String text, int max) {
        if (text == null) throw new IllegalArgumentException("This field is required.");
        String normalized = EDGE_SPACE.matcher(text).replaceAll("");
        int length = codePoints(normalized);
        if (length == 0 || length > max) throw new IllegalArgumentException("Text must contain 1 to " + max + " characters.");
        return normalized;
    }
    public static String password(String text) {
        int length = codePoints(text);
        if (length < 8 || length > 128 || !Pattern.compile("[A-Za-z]").matcher(text).find() || !Pattern.compile("[0-9]").matcher(text).find())
            throw new IllegalArgumentException("Password must contain 8 to 128 characters, including a letter and a number.");
        return text;
    }
    public static int codePoints(String text) {
        if (text == null) throw new IllegalArgumentException("This field is required.");
        for (int i=0; i<text.length(); i++) {
            char c=text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) throw new IllegalArgumentException("Text contains invalid Unicode.");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Text contains invalid Unicode.");
        }
        return text.codePointCount(0,text.length());
    }
}
