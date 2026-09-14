package com.maimai.support.ai;

/** Plain text storage: strip presentation syntax, never execute or render model markup. */
final class PlainReply {
    private PlainReply() {}
    static String clean(String value) {
        return value.replaceAll("(?m)^\\s*```[^\\n]*\\n?", "")
            .replaceAll("(?m)^\\s{0,3}#{1,6}\\s+", "")
            .replaceAll("(?m)^\\s*>\\s?", "")
            .replaceAll("\\*\\*([^*]+)\\*\\*", "$1").replaceAll("__([^_]+)__", "$1")
            .replaceAll("~~([^~]+)~~", "$1").replaceAll("`([^`]+)`", "$1")
            .replaceAll("\\*([^*\\n]+)\\*", "$1")
            .replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "$1（$2）")
            .replaceAll("(?m)^\\s*[-*]\\s+", "• ").replaceAll("\\n{3,}", "\n\n").strip();
    }
}
