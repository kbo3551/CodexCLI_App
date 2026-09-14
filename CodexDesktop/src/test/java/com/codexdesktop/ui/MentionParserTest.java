package com.codexdesktop.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Rules for the {@code @} token under the caret.
 *
 * <p>This is what decides whether the file picker opens at all. It previously read the caret one
 * character behind — JavaFX fires the text listener before moving the caret — so the fragment was
 * empty on the first keystroke and the picker never appeared. Hence the offset cases below.
 */
class MentionParserTest {

    @Test
    void findsTheFragmentBeforeTheCaret() {
        assertEquals("lo", MentionParser.fragmentAt("@lo", 3));
        assertEquals("l", MentionParser.fragmentAt("@lo", 2));
        assertEquals("log4j", MentionParser.fragmentAt("open @log4j", 11));
    }

    /** The caret can legitimately trail the text by a character; that must still resolve. */
    @Test
    void worksWhenTheCaretTrailsTheText() {
        assertEquals("l", MentionParser.fragmentAt("@lo", 2));
        assertNull(MentionParser.fragmentAt("@l", 1));
    }

    @Test
    void requiresTheAtToStartAWord() {
        assertNull(MentionParser.fragmentAt("mail@example", 12));
        assertEquals("example", MentionParser.fragmentAt("mail @example", 13));
    }

    @Test
    void stopsAtWhitespace() {
        assertNull(MentionParser.fragmentAt("@log then", 9));
        assertEquals("log", MentionParser.fragmentAt("@log then", 4));
    }

    @Test
    void returnsNullWithoutAToken() {
        assertNull(MentionParser.fragmentAt("", 0));
        assertNull(MentionParser.fragmentAt("hello", 5));
        assertNull(MentionParser.fragmentAt("@", 1));
        assertNull(MentionParser.fragmentAt(null, 3));
    }

    @Test
    void reportsTheTokenStartForReplacement() {
        assertEquals(0, MentionParser.tokenStart("@lo", 3));
        assertEquals(6, MentionParser.tokenStart("check @lo", 9));
        assertEquals(-1, MentionParser.tokenStart("no token", 8));
    }

    /** Only the token the caret is in matters, not an earlier one. */
    @Test
    void usesTheNearestTokenToTheCaret() {
        assertEquals("second", MentionParser.fragmentAt("@first and @second", 18));
        assertEquals(11, MentionParser.tokenStart("@first and @second", 18));
    }

    @Test
    void toleratesACaretBeyondTheText() {
        assertEquals("lo", MentionParser.fragmentAt("@lo", 99));
    }
}
