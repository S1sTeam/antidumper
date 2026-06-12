package com.s1steam.antidumper.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("(&)?#([0-9a-fA-F]{6})");

    private ColorUtil() {}

    public static String translate(String input) {
        if (input == null || input.isEmpty()) return input;

        String result = translateHex(input);
        result = translateLegacy(result);
        return result;
    }

    private static String translateHex(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String prefix = matcher.group(1);
            String hex = matcher.group(2);

            if ("&".equals(prefix)) {
                StringBuilder replacement = new StringBuilder("§x");
                for (char c : hex.toCharArray()) {
                    replacement.append('§').append(c);
                }
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement("#" + hex));
            }
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String translateLegacy(String input) {
        char[] chars = input.toCharArray();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '&' && i + 1 < chars.length) {
                char next = chars[i + 1];
                if ("0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(next) != -1) {
                    sb.append('§').append(next);
                    i++;
                    continue;
                }
            }
            sb.append(chars[i]);
        }

        return sb.toString();
    }
}
