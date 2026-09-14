package com.codexdesktop.ui.conversation;

import com.codexdesktop.ui.Ui;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.function.Supplier;

/**
 * The compact card used for every Codex action (read, search, command, file edit).
 *
 * <p>Collapsed it is a single 30 px row: kind, target, optional trailing badge. Detail content is
 * produced by a supplier the first time the row is clicked, so output and diffs cost nothing
 * until someone looks at them.
 */
public class ToolCard extends VBox {

    private final Label kindLabel;
    private final Label targetLabel;
    private final HBox header;
    private final Label chevron;
    private Node trailing;
    private Supplier<Node> detailSupplier;
    private Node detail;
    private boolean expanded;

    public ToolCard(String kind, String target) {
        getStyleClass().add("tool-card");

        kindLabel = Ui.label(kind, "tool-kind");
        // Never let the kind shrink: in a narrow column the HBox squeezed it down to an ellipsis
        // and the card lost its "Read"/"Command" prefix. The target truncates instead.
        kindLabel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        targetLabel = Ui.label(target, "tool-target");
        targetLabel.setMaxWidth(Double.MAX_VALUE);
        chevron = Ui.label("\u203a", "text-muted");
        chevron.setVisible(false);

        header = Ui.row(8, chevron, kindLabel, targetLabel, Ui.hSpacer());
        header.getStyleClass().add("tool-header");
        header.setOnMouseClicked(event -> {
            if (detailSupplier != null) {
                setExpanded(!expanded);
            }
        });
        getChildren().add(header);
    }

    public void setKind(String kind) {
        kindLabel.setText(kind);
    }

    public void setTarget(String target) {
        targetLabel.setText(target);
    }

    /** Right-aligned node: a status glyph, "+3 -1", a duration, … */
    public void setTrailing(Node node) {
        if (trailing != null) {
            header.getChildren().remove(trailing);
        }
        trailing = node;
        if (node != null) {
            header.getChildren().add(node);
        }
    }

    /** Registering a supplier makes the card expandable. */
    public void setDetailSupplier(Supplier<Node> supplier) {
        this.detailSupplier = supplier;
        chevron.setVisible(supplier != null);
        if (supplier == null && detail != null) {
            getChildren().remove(detail);
            detail = null;
            expanded = false;
        }
    }

    /** Drops a built detail node so the next expand rebuilds it from current data. */
    public void invalidateDetail() {
        if (detail != null) {
            getChildren().remove(detail);
            detail = null;
            if (expanded) {
                buildDetail();
            }
        }
    }

    public void setExpanded(boolean value) {
        if (value == expanded) {
            return;
        }
        expanded = value;
        chevron.setText(expanded ? "\u2304" : "\u203a");
        if (expanded) {
            buildDetail();
        } else if (detail != null) {
            getChildren().remove(detail);
            detail = null;
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** Marks the card as running / failed so the border can reflect state. */
    public void setState(String state) {
        Ui.toggleClass(this, "state-running", "running".equals(state));
        Ui.toggleClass(this, "state-failed", "failed".equals(state));
    }

    private void buildDetail() {
        if (detailSupplier == null || detail != null) {
            return;
        }
        Node built = detailSupplier.get();
        if (built == null) {
            return;
        }
        VBox wrapper = new VBox(built);
        wrapper.getStyleClass().add("tool-detail");
        detail = wrapper;
        getChildren().add(wrapper);
    }
}
