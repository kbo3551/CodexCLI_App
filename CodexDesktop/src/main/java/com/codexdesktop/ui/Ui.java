package com.codexdesktop.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Small factory helpers so component code stays about layout, not boilerplate. */
public final class Ui {

    private Ui() {
    }

    public static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    public static Button button(String text, String... styleClasses) {
        Button button = new Button(text);
        button.getStyleClass().addAll(styleClasses);
        return button;
    }

    public static Button iconButton(String glyph, String tooltip) {
        Button button = new Button(glyph);
        button.getStyleClass().addAll("button", "icon-button");
        button.setFocusTraversable(false);
        if (tooltip != null && !tooltip.isBlank()) {
            attachTooltip(button, tooltip);
        }
        return button;
    }

    public static void attachTooltip(Node node, String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(Duration.millis(400));
        Tooltip.install(node, tooltip);
    }

    public static Region hSpacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public static Region vSpacer() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    public static HBox row(double spacing, Node... children) {
        HBox box = new HBox(spacing, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static VBox column(double spacing, Node... children) {
        return new VBox(spacing, children);
    }

    public static <T extends Region> T padded(T region, double top, double right, double bottom, double left) {
        region.setPadding(new Insets(top, right, bottom, left));
        return region;
    }

    /** Adds or removes a style class without duplicating it. */
    public static void toggleClass(Node node, String styleClass, boolean enabled) {
        if (enabled) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }

    /** Truncates in the middle, which keeps both the parent folder and file name readable. */
    public static String ellipsizeMiddle(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        int keep = (maxLength - 1) / 2;
        return value.substring(0, keep) + "\u2026" + value.substring(value.length() - keep);
    }

    /** Last path segment of a POSIX or Windows path. */
    public static String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index < 0 ? normalized : normalized.substring(index + 1);
    }

    public static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        }
        double seconds = millis / 1000.0;
        if (seconds < 60) {
            return String.format("%.1fs", seconds);
        }
        long minutes = (long) (seconds / 60);
        return minutes + "m " + Math.round(seconds - minutes * 60L) + "s";
    }
}
