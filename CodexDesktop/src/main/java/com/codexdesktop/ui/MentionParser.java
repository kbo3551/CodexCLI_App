package com.codexdesktop.ui;

/**
 * Finds the {@code @token} the caret is sitting in.
 *
 * <p>Pulled out of the composer so the rules are testable without a window: the text listener and
 * the caret listener both feed it, and getting the boundaries wrong is what silently stops the file
 * picker from appearing.
 */
public final class MentionParser {

    private MentionParser() {
    }

    /**
     * @param text  full composer text
     * @param caret caret offset
     * @return the characters between the {@code @} and the caret, or null when the caret is not
     *         inside a mention token
     */
    public static String fragmentAt(String text, int caret) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int position = Math.max(0, Math.min(caret, text.length()));
        if (position == 0) {
            return null;
        }
        int at = text.lastIndexOf('@', position - 1);
        if (at < 0 || at >= position) {
            return null;
        }
        // The @ has to start a word, otherwise an email address would open the picker.
        if (at > 0 && !Character.isWhitespace(text.charAt(at - 1))) {
            return null;
        }
        String fragment = text.substring(at + 1, position);
        if (fragment.isEmpty() || fragment.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        return fragment;
    }

    /** Offset of the {@code @} that starts the token the caret is in, or -1. */
    public static int tokenStart(String text, int caret) {
        if (fragmentAt(text, caret) == null) {
            return -1;
        }
        int position = Math.max(0, Math.min(caret, text.length()));
        return text.lastIndexOf('@', position - 1);
    }
}
