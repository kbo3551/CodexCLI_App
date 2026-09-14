package com.codexdesktop.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@code fuzzyFileSearch} result shape. Sample captured from codex-cli 0.154.0: the server
 * returns the search root separately from a project-relative path, and marks folders with
 * {@code match_type}.
 */
class FileMatchTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildsAnAbsolutePathFromRootAndRelativePath() throws Exception {
        FileMatch match = FileMatch.from(mapper.readTree("""
                {"root":"/mnt/c/dev/CodexDesktop",
                 "path":"src/main/java/com/codexdesktop/ui/Composer.java",
                 "match_type":"file","file_name":"Composer.java","score":209,"indices":null}"""));

        assertEquals("Composer.java", match.fileName());
        assertFalse(match.directory());
        assertEquals("/mnt/c/dev/CodexDesktop/src/main/java/com/codexdesktop/ui/Composer.java",
                match.absolutePath());
        assertEquals("src/main/java/com/codexdesktop/ui", match.parentPath());
        assertEquals(209, match.score());
    }

    @Test
    void recognisesDirectories() throws Exception {
        FileMatch match = FileMatch.from(mapper.readTree("""
                {"root":"/mnt/c/dev","path":"src/main/java/com/codexdesktop/ui/office",
                 "match_type":"directory","file_name":"office","score":159}"""));

        assertTrue(match.directory());
        assertEquals("/mnt/c/dev/src/main/java/com/codexdesktop/ui/office", match.absolutePath());
    }

    @Test
    void handlesATrailingSlashOnTheRoot() throws Exception {
        FileMatch match = FileMatch.from(mapper.readTree(
                "{\"root\":\"/mnt/c/dev/\",\"path\":\"pom.xml\",\"file_name\":\"pom.xml\"}"));

        assertEquals("/mnt/c/dev/pom.xml", match.absolutePath());
        assertEquals("", match.parentPath());
    }

    @Test
    void survivesAMissingRoot() throws Exception {
        FileMatch match = FileMatch.from(mapper.readTree(
                "{\"path\":\"pom.xml\",\"file_name\":\"pom.xml\"}"));

        assertEquals("pom.xml", match.absolutePath());
    }
}
