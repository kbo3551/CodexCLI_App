package com.codexdesktop.codex;

import com.codexdesktop.i18n.I18n;

/** Lifecycle of the connection to the WSL-hosted app-server, as shown in the status bar. */
public enum ConnectionState {
    DISCONNECTED("status.disconnected"),
    CONNECTING("status.connecting"),
    CONNECTED("status.connected"),
    FAILED("status.unavailable");

    private final String messageKey;

    ConnectionState(String messageKey) {
        this.messageKey = messageKey;
    }

    /** Resolved on each call so a language change is reflected without recreating the enum. */
    public String label() {
        return I18n.t(messageKey);
    }
}
