package com.codexdesktop.ui;

import com.codexdesktop.i18n.I18n;
import com.codexdesktop.model.AppSettings;
import com.codexdesktop.service.DiagnosticsService;
import com.codexdesktop.service.SettingsService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Consumer;

/**
 * Settings and environment diagnostics.
 *
 * <p>Diagnostics runs the same commands the app itself uses and shows each command next to its
 * result, so a failure is reproducible in a terminal instead of being an opaque error.
 */
public final class SettingsView extends VBox {

    private final TextField wslExecutable = new TextField();
    private final TextField distribution = new TextField();
    private final TextField codexExecutable = new TextField();
    private final TextField codexArguments = new TextField();
    private final ComboBox<String> theme = new ComboBox<>();
    private final ComboBox<String> language = new ComboBox<>();
    private final ComboBox<String> fontScale = new ComboBox<>();
    private final ComboBox<String> uiFont = new ComboBox<>();
    private final ComboBox<String> approvalPolicy = new ComboBox<>();
    private final ComboBox<String> sandboxMode = new ComboBox<>();
    private final CheckBox debugLogging = new CheckBox(I18n.t("settings.debugLogging"));

    private final VBox diagnosticsResults = new VBox(2);
    private final Label diagnosticsHint = Ui.label("", "caption");

    private final DiagnosticsService diagnosticsService;
    private Consumer<AppSettings> onSave = settings -> { };
    private Runnable onClose = () -> { };

    public SettingsView(DiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;

        Label title = Ui.label(I18n.t("settings.title"), "title-strong");
        var closeIcon = Ui.iconButton("\u00d7", I18n.t("settings.close"));
        closeIcon.setOnAction(event -> onClose.run());
        HBox header = Ui.row(8, title, Ui.hSpacer(), closeIcon);
        header.getStyleClass().add("top-bar");

        theme.getItems().setAll(AppSettings.THEME_SYSTEM, AppSettings.THEME_LIGHT, AppSettings.THEME_DARK);
        theme.setConverter(labels("theme.system", "theme.light", "theme.dark",
                AppSettings.THEME_SYSTEM, AppSettings.THEME_LIGHT, AppSettings.THEME_DARK));

        language.getItems().setAll(I18n.LANGUAGE_SYSTEM, I18n.LANGUAGE_ENGLISH, I18n.LANGUAGE_KOREAN);
        language.setConverter(labels("language.system", "language.english", "language.korean",
                I18n.LANGUAGE_SYSTEM, I18n.LANGUAGE_ENGLISH, I18n.LANGUAGE_KOREAN));

        fontScale.getItems().setAll(AppSettings.SCALE_COMPACT, AppSettings.SCALE_NORMAL, AppSettings.SCALE_LARGE);
        fontScale.setConverter(labels("fontScale.compact", "fontScale.normal", "fontScale.large",
                AppSettings.SCALE_COMPACT, AppSettings.SCALE_NORMAL, AppSettings.SCALE_LARGE));

        // A blank entry keeps the stylesheet's own font stack; the rest are what is installed.
        uiFont.getItems().add(AppSettings.FONT_DEFAULT);
        uiFont.getItems().addAll(FontLoader.availableUiFonts());
        uiFont.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return value == null || value.isBlank() ? I18n.t("settings.fontDefault") : value;
            }

            @Override
            public String fromString(String label) {
                return I18n.t("settings.fontDefault").equals(label) ? AppSettings.FONT_DEFAULT : label;
            }
        });

        String inherit = I18n.t("settings.inherit");
        approvalPolicy.getItems().setAll(inherit, "untrusted", "on-request", "never");
        sandboxMode.getItems().setAll(inherit, "read-only", "workspace-write", "danger-full-access");

        VBox runtimeGroup = group(I18n.t("settings.runtime"),
                row(I18n.t("settings.wslExecutable"), wslExecutable),
                row(I18n.t("settings.distribution"), distribution, I18n.t("settings.distributionHint")),
                row(I18n.t("settings.codexExecutable"), codexExecutable, I18n.t("settings.codexExecutableHint")),
                row(I18n.t("settings.codexArguments"), codexArguments));

        VBox policyGroup = group(I18n.t("settings.policy"),
                row(I18n.t("settings.approvalPolicy"), approvalPolicy, I18n.t("settings.approvalPolicyHint")),
                row(I18n.t("settings.sandbox"), sandboxMode, I18n.t("settings.sandboxHint")));

        VBox appearanceGroup = group(I18n.t("settings.appearance"),
                row(I18n.t("settings.theme"), theme),
                row(I18n.t("settings.language"), language),
                row(I18n.t("settings.font"), uiFont, I18n.t("settings.fontHint")),
                row(I18n.t("settings.fontScale"), fontScale, I18n.t("settings.fontScaleHint")));

        var openLogs = Ui.button(I18n.t("settings.openLogFolder"), "link-button");
        openLogs.setOnAction(event -> openLogFolder());
        debugLogging.setWrapText(true);
        debugLogging.setMaxWidth(Double.MAX_VALUE);
        Label debugHint = Ui.label(I18n.t("settings.debugLoggingHint"), "caption");
        debugHint.setWrapText(true);
        VBox appGroup = group(I18n.t("settings.application"),
                new VBox(6, debugLogging, debugHint, openLogs));

        var runDiagnostics = Ui.button(I18n.t("settings.runDiagnostics"), "button", "subtle-outline");
        runDiagnostics.setOnAction(event -> runDiagnostics(runDiagnostics));
        VBox diagnosticsGroup = group(I18n.t("settings.diagnostics"),
                new VBox(10, Ui.row(10, runDiagnostics, diagnosticsHint), diagnosticsResults));

        var saveButton = Ui.button(I18n.t("settings.save"), "button", "primary");
        saveButton.setOnAction(event -> onSave.accept(collect()));
        var cancelButton = Ui.button(I18n.t("settings.closeButton"), "button", "subtle-outline");
        cancelButton.setOnAction(event -> onClose.run());
        HBox actions = Ui.row(8, Ui.hSpacer(), cancelButton, saveButton);

        VBox page = new VBox(16, runtimeGroup, policyGroup, appearanceGroup, appGroup, diagnosticsGroup, actions);
        page.getStyleClass().add("settings-page");
        page.setMaxWidth(640);

        // Centred so the form does not hug the left edge on a wide window.
        VBox pageHolder = new VBox(page);
        pageHolder.setAlignment(Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(pageHolder);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(header, scroll);
    }

    public void setOnSave(Consumer<AppSettings> handler) {
        this.onSave = handler;
    }

    public void setOnClose(Runnable handler) {
        this.onClose = handler;
    }

    public void load(AppSettings settings) {
        wslExecutable.setText(settings.wslExecutable());
        distribution.setText(settings.distribution());
        codexExecutable.setText(settings.codexExecutable());
        codexArguments.setText(settings.codexArguments());
        theme.setValue(settings.theme());
        language.setValue(settings.language());
        fontScale.setValue(settings.fontScale());
        uiFont.setValue(settings.uiFont());
        String inherit = I18n.t("settings.inherit");
        approvalPolicy.setValue(settings.approvalPolicy().isBlank() ? inherit : settings.approvalPolicy());
        sandboxMode.setValue(settings.sandboxMode().isBlank() ? inherit : settings.sandboxMode());
        debugLogging.setSelected(settings.debugLogging());
    }

    private AppSettings collect() {
        String inherit = I18n.t("settings.inherit");
        return new AppSettings(
                wslExecutable.getText(),
                distribution.getText(),
                codexExecutable.getText(),
                codexArguments.getText(),
                theme.getValue(),
                language.getValue(),
                fontScale.getValue(),
                uiFont.getValue(),
                debugLogging.isSelected(),
                inherit.equals(approvalPolicy.getValue()) ? "" : approvalPolicy.getValue(),
                inherit.equals(sandboxMode.getValue()) ? "" : sandboxMode.getValue()).normalized();
    }

    private void openLogFolder() {
        try {
            java.nio.file.Path logs = SettingsService.appDataDirectory().resolve("logs");
            java.nio.file.Files.createDirectories(logs);
            new ProcessBuilder("explorer.exe", logs.toString()).start();
        } catch (Exception e) {
            diagnosticsHint.setText(e.getMessage());
        }
    }

    private void runDiagnostics(javafx.scene.control.Button trigger) {
        trigger.setDisable(true);
        diagnosticsHint.setText(I18n.t("settings.diagnosticsRunning"));
        diagnosticsResults.getChildren().clear();
        AppSettings snapshot = collect();
        Thread.ofVirtual().name("diagnostics").start(() -> {
            DiagnosticsService.Report report = diagnosticsService.run(snapshot);
            Platform.runLater(() -> {
                renderReport(report.checks());
                diagnosticsHint.setText(report.allOk()
                        ? I18n.t("settings.environmentReady") : I18n.t("settings.checksFailed"));
                if (!report.detectedCodexPath().isBlank()
                        && !codexExecutable.getText().equals(report.detectedCodexPath())) {
                    diagnosticsResults.getChildren().add(detectedPathRow(report.detectedCodexPath()));
                }
                trigger.setDisable(false);
            });
        });
    }

    private void renderReport(List<DiagnosticsService.Check> checks) {
        diagnosticsResults.getChildren().clear();
        for (DiagnosticsService.Check check : checks) {
            String glyph = switch (check.status()) {
                case OK -> "\u2713";
                case WARN -> "!";
                case FAIL -> "\u2715";
            };
            String styleClass = switch (check.status()) {
                case OK -> "text-success";
                case WARN -> "text-secondary";
                case FAIL -> "text-danger";
            };
            Label mark = Ui.label(glyph, styleClass);
            mark.setMinWidth(14);
            Label name = Ui.label(check.name(), "label");
            name.setMinWidth(140);
            Label detail = Ui.label(check.detail(), "text-secondary");
            detail.setWrapText(true);
            VBox text = new VBox(1, Ui.row(8, name, detail));
            if (check.status() != DiagnosticsService.Status.OK) {
                text.getChildren().add(Ui.label(check.command(), "diagnostics-command"));
            }
            HBox row = Ui.row(8, mark, text);
            row.getStyleClass().add("diagnostics-row");
            row.setAlignment(Pos.TOP_LEFT);
            diagnosticsResults.getChildren().add(row);
        }
    }

    private HBox detectedPathRow(String detectedPath) {
        var useButton = Ui.button(I18n.t("settings.use", detectedPath), "link-button");
        useButton.setOnAction(event -> codexExecutable.setText(detectedPath));
        return Ui.row(8, Ui.label(I18n.t("settings.detected"), "caption"), useButton);
    }

    /** Shows translated labels while keeping the stored value language-independent. */
    private static StringConverter<String> labels(String key1, String key2, String key3,
                                                 String value1, String value2, String value3) {
        return new StringConverter<>() {
            @Override
            public String toString(String value) {
                if (value == null) {
                    return "";
                }
                if (value.equals(value1)) {
                    return I18n.t(key1);
                }
                if (value.equals(value2)) {
                    return I18n.t(key2);
                }
                if (value.equals(value3)) {
                    return I18n.t(key3);
                }
                return value;
            }

            @Override
            public String fromString(String label) {
                if (I18n.t(key1).equals(label)) {
                    return value1;
                }
                if (I18n.t(key2).equals(label)) {
                    return value2;
                }
                if (I18n.t(key3).equals(label)) {
                    return value3;
                }
                return label;
            }
        };
    }

    /**
     * A settings group. Rows go into a {@link GridPane} so the label and control columns line up
     * across every group — with plain HBoxes the field widths drifted because each row's hint
     * absorbed a different amount of leftover space.
     */
    private static VBox group(String title, Object... rows) {
        VBox box = new VBox(10);
        box.getStyleClass().add("settings-group");
        box.getChildren().add(Ui.label(title, "group-title"));

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(140);
        labelColumn.setPrefWidth(140);
        labelColumn.setHalignment(javafx.geometry.HPos.LEFT);
        ColumnConstraints controlColumn = new ColumnConstraints();
        controlColumn.setMinWidth(240);
        controlColumn.setPrefWidth(300);
        controlColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelColumn, controlColumn);

        int rowIndex = 0;
        for (Object entry : rows) {
            if (entry instanceof SettingsRow row) {
                Label name = Ui.label(row.label(), "settings-row-label");
                grid.add(name, 0, rowIndex);
                GridPane.setValignment(name, javafx.geometry.VPos.CENTER);

                VBox controlBox = new VBox(4);
                if (row.control() instanceof Region region) {
                    region.setMaxWidth(Double.MAX_VALUE);
                }
                controlBox.getChildren().add(row.control());
                if (row.hint() != null && !row.hint().isBlank()) {
                    // The hint lives under its field instead of beside it: at this width a
                    // side-by-side hint wrapped mid-sentence and pushed the fields out of line.
                    Label hint = Ui.label(row.hint(), "caption");
                    hint.setWrapText(true);
                    controlBox.getChildren().add(hint);
                }
                grid.add(controlBox, 1, rowIndex);
                rowIndex++;
            } else if (entry instanceof javafx.scene.Node node) {
                // Free-form content (checkbox block, diagnostics) spans both columns.
                grid.add(node, 0, rowIndex, 2, 1);
                rowIndex++;
            }
        }
        box.getChildren().add(grid);
        return box;
    }

    /** Carrier for one label/control/hint triple; turned into grid cells by {@link #group}. */
    private record SettingsRow(String label, javafx.scene.Node control, String hint) { }

    private static SettingsRow row(String label, javafx.scene.Node control) {
        return new SettingsRow(label, control, null);
    }

    private static SettingsRow row(String label, javafx.scene.Node control, String hint) {
        return new SettingsRow(label, control, hint);
    }
}
