package com.gongwen.assistant.documentstructure;

import java.util.regex.Pattern;

public final class DocumentHeadingRoleDetector {
    private static final Pattern LEVEL_1 = Pattern.compile("^([一二三四五六七八九十]+[、.．])\\s*\\S+");
    private static final Pattern LEVEL_2 = Pattern.compile("^[（(][一二三四五六七八九十]+[）)]\\s*\\S+");
    private static final Pattern LEVEL_3 = Pattern.compile("^\\d+[.．、)）]\\s*\\S+");

    private DocumentHeadingRoleDetector() {
    }

    public static String detect(String text) {
        String normalized = text == null ? "" : text.strip();
        if (normalized.isBlank()) {
            return "";
        }
        if ("结束语".equals(normalized)
                || LEVEL_1.matcher(normalized).matches()
                || normalized.matches("^第[一二三四五六七八九十\\d]+[章节部分].+")) {
            return "BODY_HEADING_LEVEL_1";
        }
        if (LEVEL_2.matcher(normalized).matches()) {
            return "BODY_HEADING_LEVEL_2";
        }
        if (LEVEL_3.matcher(normalized).matches()) {
            return "BODY_HEADING_LEVEL_3";
        }
        return "";
    }

    public static boolean isHeadingRole(String role) {
        return role != null && role.strip().toUpperCase().startsWith("BODY_HEADING_LEVEL_");
    }
}
