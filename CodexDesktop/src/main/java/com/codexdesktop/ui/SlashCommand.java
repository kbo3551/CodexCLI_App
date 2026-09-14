package com.codexdesktop.ui;

/**
 * One entry in the composer's slash-command palette.
 *
 * @param name        command word without the slash, e.g. {@code new}
 * @param description one-line explanation shown next to it
 * @param action      what to run when it is chosen
 */
public record SlashCommand(String name, String description, Runnable action) {

    public String display() {
        return "/" + name;
    }

    /** Case-insensitive prefix match against what the user has typed after the slash. */
    public boolean matches(String typed) {
        return name.startsWith(typed.toLowerCase(java.util.Locale.ROOT));
    }
}
