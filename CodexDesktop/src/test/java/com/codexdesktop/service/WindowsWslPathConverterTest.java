package com.codexdesktop.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowsWslPathConverterTest {

    /**
     * The runner is only a fallback for exotic paths; every case below must be handled in-process,
     * so a runner pointing at a non-existent executable would fail the test if it were consulted.
     */
    private final WindowsWslPathConverter converter =
            new WindowsWslPathConverter(new WslCommandRunner("wsl-does-not-exist.exe", ""));

    @Test
    void convertsDriveLetterPaths() {
        assertEquals("/mnt/c/Users/USER/dev/project",
                converter.toWslPath("C:\\Users\\USER\\dev\\project"));
        assertEquals("/mnt/d/work", converter.toWslPath("D:\\work"));
        assertEquals("/mnt/c", converter.toWslPath("C:\\"));
    }

    @Test
    void lowercasesTheDriveLetter() {
        assertEquals("/mnt/e/Repos", converter.toWslPath("E:\\Repos"));
    }

    @Test
    void stripsTrailingSeparator() {
        assertEquals("/mnt/c/Users/USER/dev",
                converter.toWslPath("C:\\Users\\USER\\dev\\"));
    }

    @Test
    void acceptsForwardSlashes() {
        assertEquals("/mnt/c/Users/USER/dev",
                converter.toWslPath("C:/Users/USER/dev"));
    }

    @Test
    void passesThroughLinuxPaths() {
        assertEquals("/home/boryeong/project", converter.toWslPath("/home/boryeong/project"));
    }

    @Test
    void mapsWslUncPathsBackToLinuxPaths() {
        assertEquals("/home/boryeong/project",
                converter.toWslPath("\\\\wsl$\\Ubuntu-24.04\\home\\boryeong\\project"));
        assertEquals("/home/boryeong",
                converter.toWslPath("\\\\wsl.localhost\\Ubuntu-24.04\\home\\boryeong"));
    }

    @Test
    void rejectsEmptyPaths() {
        assertThrows(WindowsWslPathConverter.PathConversionException.class,
                () -> converter.toWslPath("  "));
    }
}
