package com.codexdesktop.ui;

import com.codexdesktop.i18n.I18n;
import com.codexdesktop.model.Attachment;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DragEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The input area: text, attachments, send/stop.
 *
 * <p>Enter sends, Shift+Enter inserts a newline. Files arrive three ways — the attach button,
 * drag-and-drop onto the composer, and Ctrl+V of an image or file list — matching how the CLI is
 * used with {@code @file} mentions and pasted screenshots.
 */
public final class Composer extends VBox {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Composer.class);

    private static final int MIN_ROWS = 1;
    private static final int MAX_ROWS = 8;
    private static final double ROW_HEIGHT = 21;

    private final TextArea input = new TextArea();
    private final Button sendButton = Ui.button("\u2191", "send-button");
    private final Button attachButton = Ui.iconButton("@", I18n.t("composer.attach"));
    private final FlowPane attachmentChips = new FlowPane(6, 6);
    private final ObservableList<Attachment> attachments = FXCollections.observableArrayList();
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final BooleanProperty ready = new SimpleBooleanProperty(false);
    private final HBox bar;
    private final SlashCommandPopup slashPopup = new SlashCommandPopup();
    private final MentionPopup mentionPopup = new MentionPopup();
    private final javafx.animation.PauseTransition mentionDebounce =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(140));
    private final java.util.LinkedHashMap<String, Attachment> mentions = new java.util.LinkedHashMap<>();
    private java.util.List<SlashCommand> slashCommands = java.util.List.of();
    private java.util.function.Function<String,
            java.util.concurrent.CompletableFuture<List<com.codexdesktop.model.FileMatch>>> fileSearch;
    private long mentionSequence;

    private BiConsumer<String, List<Attachment>> onSend = (text, files) -> { };
    private Runnable onStop = () -> { };
    private Consumer<List<Path>> onFilesDropped = paths -> { };
    private Runnable onAttachRequested = () -> { };
    private Consumer<javafx.scene.image.Image> onImagePasted = image -> { };

    public Composer() {
        getStyleClass().add("composer-region");

        input.setPromptText(I18n.t("composer.prompt"));
        input.getStyleClass().add("composer-input");
        input.setWrapText(true);
        input.setPrefRowCount(MIN_ROWS);
        setInputHeight(1);

        input.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        input.textProperty().addListener((observable, oldValue, newValue) -> {
            resizeToContent(newValue);
            updateSendEnabled();
            updateSlashPopup(newValue);
            updateMentionPopup();
        });
        // JavaFX fires the text listener before the caret moves, so on the first character the
        // fragment would still be empty. The caret listener is what actually opens the picker.
        input.caretPositionProperty().addListener((observable, oldValue, newValue) -> updateMentionPopup());

        attachButton.setFocusTraversable(false);
        attachButton.setOnAction(event -> onAttachRequested.run());

        sendButton.setFocusTraversable(false);
        Ui.attachTooltip(sendButton, I18n.t("composer.send"));
        sendButton.setOnAction(event -> {
            if (busy.get()) {
                onStop.run();
            } else {
                fireSend();
            }
        });

        attachmentChips.setPadding(new Insets(0, 0, 6, 2));
        attachmentChips.setVisible(false);
        attachmentChips.setManaged(false);
        attachments.addListener((javafx.collections.ListChangeListener<Attachment>) change -> renderChips());

        bar = new HBox(6, attachButton, input, sendButton);
        bar.setAlignment(Pos.BOTTOM_LEFT);
        bar.getStyleClass().add("composer");
        HBox.setHgrow(input, Priority.ALWAYS);

        VBox stack = new VBox(0, attachmentChips, bar);
        getChildren().add(stack);

        input.focusedProperty().addListener((observable, oldValue, focused) ->
                Ui.toggleClass(bar, "focused", focused));

        busy.addListener((observable, oldValue, isBusy) -> {
            sendButton.setText(isBusy ? "\u25a0" : "\u2191");
            Ui.toggleClass(sendButton, "stop", isBusy);
            Ui.attachTooltip(sendButton, isBusy ? I18n.t("composer.stop") : I18n.t("composer.send"));
            updateSendEnabled();
        });
        ready.addListener((observable, oldValue, isReady) -> {
            updateSendEnabled();
            input.setDisable(!isReady);
            attachButton.setDisable(!isReady);
        });
        input.setDisable(true);
        attachButton.setDisable(true);
        updateSendEnabled();

        slashPopup.setOnChosen(command -> {
            input.clear();
            command.action().run();
        });
        mentionPopup.setOnChosen(this::insertMention);
        // Losing focus (clicking the transcript, switching windows) should not leave it hanging.
        input.focusedProperty().addListener((observable, was, focused) -> {
            if (!focused) {
                slashPopup.hide();
                mentionPopup.hide();
            }
        });

        installDragAndDrop();
    }

    public void setOnSend(BiConsumer<String, List<Attachment>> handler) {
        this.onSend = handler;
    }

    public void setOnStop(Runnable handler) {
        this.onStop = handler;
    }

    public void setOnFilesDropped(Consumer<List<Path>> handler) {
        this.onFilesDropped = handler;
    }

    public void setOnAttachRequested(Runnable handler) {
        this.onAttachRequested = handler;
    }

    public void setOnImagePasted(Consumer<javafx.scene.image.Image> handler) {
        this.onImagePasted = handler;
    }

    public void addAttachment(Attachment attachment) {
        if (attachments.stream().noneMatch(existing -> existing.wslPath().equals(attachment.wslPath()))) {
            attachments.add(attachment);
        }
    }

    public List<Attachment> attachments() {
        return List.copyOf(attachments);
    }

    public void clearAttachments() {
        attachments.clear();
    }

    /** True while a turn is running: the button acts as Stop. */
    public BooleanProperty busyProperty() {
        return busy;
    }

    /** True once a thread exists and messages can be sent. */
    public BooleanProperty readyProperty() {
        return ready;
    }

    public void focusInput() {
        input.requestFocus();
    }

    // ------------------------------------------------------------------ internals

    private void installDragAndDrop() {
        setOnDragOver((DragEvent event) -> {
            if (event.getDragboard().hasFiles() && ready.get()) {
                event.acceptTransferModes(TransferMode.COPY);
                Ui.toggleClass(bar, "focused", true);
            }
            event.consume();
        });
        setOnDragExited(event -> {
            Ui.toggleClass(bar, "focused", input.isFocused());
            event.consume();
        });
        setOnDragDropped((DragEvent event) -> {
            var dragboard = event.getDragboard();
            if (dragboard.hasFiles()) {
                onFilesDropped.accept(dragboard.getFiles().stream().map(File::toPath).toList());
                event.setDropCompleted(true);
            }
            event.consume();
        });
    }

    private void handleKeyPressed(KeyEvent event) {
        // A picker is open: let it own the navigation keys before the text area sees them.
        if (mentionPopup.isShowing()) {
            switch (event.getCode()) {
                case DOWN -> {
                    mentionPopup.moveSelection(1);
                    event.consume();
                    return;
                }
                case UP -> {
                    mentionPopup.moveSelection(-1);
                    event.consume();
                    return;
                }
                case ENTER, TAB -> {
                    if (!event.isShiftDown()) {
                        mentionPopup.choose();
                        event.consume();
                        return;
                    }
                }
                case LEFT -> {
                    mentionPopup.cycleMode(-1);
                    event.consume();
                    return;
                }
                case RIGHT -> {
                    mentionPopup.cycleMode(1);
                    event.consume();
                    return;
                }
                case ESCAPE -> {
                    mentionPopup.hide();
                    event.consume();
                    return;
                }
                default -> { }
            }
        }
        if (slashPopup.isShowing()) {
            switch (event.getCode()) {
                case DOWN -> {
                    slashPopup.moveSelection(1);
                    event.consume();
                    return;
                }
                case UP -> {
                    slashPopup.moveSelection(-1);
                    event.consume();
                    return;
                }
                case ENTER, TAB -> {
                    if (!event.isShiftDown()) {
                        slashPopup.choose();
                        event.consume();
                        return;
                    }
                }
                case ESCAPE -> {
                    slashPopup.hide();
                    event.consume();
                    return;
                }
                default -> { }
            }
        }
        if (event.isControlDown() && event.getCode() == KeyCode.V) {
            if (handlePaste()) {
                event.consume();
            }
            return;
        }
        if (event.getCode() != KeyCode.ENTER) {
            return;
        }
        if (event.isShiftDown()) {
            return; // newline
        }
        event.consume();
        if (!busy.get()) {
            fireSend();
        }
    }

    /** Shows the palette while the message is nothing but a leading slash word. */
    private void updateSlashPopup(String text) {
        if (slashCommands.isEmpty() || !ready.get()) {
            slashPopup.hide();
            return;
        }
        if (text.startsWith("/") && !text.contains(" ") && !text.contains("\n")) {
            slashPopup.applyStylesheets(stylesheets(), rootStyle());
            slashPopup.showFor(bar, slashCommands, text.substring(1));
        } else {
            slashPopup.hide();
        }
    }

    /**
     * Looks for an {@code @token} immediately before the caret and searches for it.
     *
     * <p>Debounced so a burst of keystrokes only produces one lookup, and stale answers are dropped
     * by sequence number — otherwise a slow reply for "Co" could overwrite the list for "Compo".
     */
    private void updateMentionPopup() {
        if (fileSearch == null || !ready.get()) {
            log.debug("mention: skipped (search={}, ready={})", fileSearch != null, ready.get());
            mentionPopup.hide();
            return;
        }
        String fragment = mentionFragment();
        log.debug("mention: text={} caret={} fragment={}",
                input.getText().length(), input.getCaretPosition(), fragment);
        if (fragment == null) {
            mentionPopup.hide();
            mentionDebounce.stop();
            return;
        }
        mentionDebounce.stop();
        mentionDebounce.setOnFinished(event -> runMentionSearch(fragment));
        mentionDebounce.playFromStart();
    }

    private void runMentionSearch(String fragment) {
        long sequence = ++mentionSequence;
        log.debug("mention: searching {}", fragment);
        fileSearch.apply(fragment).thenAccept(matches -> javafx.application.Platform.runLater(() -> {
            if (sequence != mentionSequence || !fragment.equals(mentionFragment())) {
                log.debug("mention: dropped stale result for {} (now {})", fragment, mentionFragment());
                return; // a newer query is already in flight
            }
            log.debug("mention: {} matches for {}", matches.size(), fragment);
            mentionPopup.applyStylesheets(stylesheets(), rootStyle());
            mentionPopup.showFor(bar, matches);
        })).exceptionally(error -> {
            log.debug("File search for {} failed: {}", fragment, error.getMessage());
            return null;
        });
    }

    /**
     * The text between the {@code @} and the caret, or null when the caret is not in a mention.
     *
     * <p>Requires the {@code @} to start a word so an email address does not trigger the picker.
     */
    private String mentionFragment() {
        return MentionParser.fragmentAt(input.getText(), input.getCaretPosition());
    }

    /** Replaces the typed {@code @fragment} with the picked path and records the mention. */
    private void insertMention(com.codexdesktop.model.FileMatch match) {
        String text = input.getText();
        int caret = Math.min(input.getCaretPosition(), text.length());
        int at = MentionParser.tokenStart(text, caret);
        if (at < 0) {
            return;
        }
        String token = match.path() + (match.directory() ? "/" : "");
        String replacement = "@" + token + " ";
        input.replaceText(at, caret, replacement);
        input.positionCaret(at + replacement.length());
        mentions.put(token, new Attachment(match.fileName(), null,
                match.absolutePath(), false, 0));
    }

    /**
     * Mentions still referenced by the text.
     *
     * <p>Deleting the {@code @path} from the message drops the mention too, so what is sent always
     * matches what the user can see.
     */
    private List<Attachment> activeMentions() {
        String text = input.getText();
        return mentions.entrySet().stream()
                .filter(entry -> text.contains("@" + entry.getKey()))
                .map(java.util.Map.Entry::getValue)
                .toList();
    }

    /** Supplies fuzzy file search results for the {@code @} picker; null disables it. */
    public void setFileSearch(java.util.function.Function<String,
            java.util.concurrent.CompletableFuture<List<com.codexdesktop.model.FileMatch>>> search) {
        this.fileSearch = search;
    }

    private java.util.List<String> stylesheets() {
        return getScene() == null ? java.util.List.of()
                : java.util.List.copyOf(getScene().getStylesheets());
    }

    private String rootStyle() {
        return getScene() == null || getScene().getRoot() == null ? "" : getScene().getRoot().getStyle();
    }

    /** Replaces the palette's command list; pass an empty list to disable the feature. */
    public void setSlashCommands(java.util.List<SlashCommand> commands) {
        this.slashCommands = commands == null ? java.util.List.of() : java.util.List.copyOf(commands);
    }

    /** @return true when the clipboard held files or an image, so the default paste is skipped */
    private boolean handlePaste() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasFiles()) {
            onFilesDropped.accept(clipboard.getFiles().stream().map(File::toPath).toList());
            return true;
        }
        if (clipboard.hasImage()) {
            onImagePasted.accept(clipboard.getImage());
            return true;
        }
        return false;
    }

    private void fireSend() {
        String text = input.getText().strip();
        if (!ready.get() || (text.isEmpty() && attachments.isEmpty())) {
            return;
        }
        List<Attachment> files = new java.util.ArrayList<>(attachments);
        files.addAll(activeMentions());
        input.clear();
        attachments.clear();
        mentions.clear();
        resizeToContent("");
        slashPopup.hide();
        mentionPopup.hide();
        onSend.accept(text, List.copyOf(files));
    }

    private void updateSendEnabled() {
        boolean canSend = ready.get() && (!input.getText().isBlank() || !attachments.isEmpty());
        sendButton.setDisable(!busy.get() && !canSend);
    }

    private void renderChips() {
        attachmentChips.getChildren().clear();
        boolean any = !attachments.isEmpty();
        attachmentChips.setVisible(any);
        attachmentChips.setManaged(any);
        for (Attachment attachment : attachments) {
            attachmentChips.getChildren().add(chip(attachment));
        }
        updateSendEnabled();
    }

    private HBox chip(Attachment attachment) {
        Label icon = Ui.label(attachment.image() ? "IMG" : "@", "text-muted");
        Label name = Ui.label(Ui.ellipsizeMiddle(attachment.name(), 34), "label");
        Label size = Ui.label(attachment.describeSize(), "caption");
        Button remove = Ui.iconButton("\u00d7", null);
        remove.setOnAction(event -> attachments.remove(attachment));
        HBox chip = Ui.row(6, icon, name, size, remove);
        chip.getStyleClass().add("attachment-chip");
        Ui.attachTooltip(chip, attachment.windowsPath() + "\n" + attachment.wslPath());
        return chip;
    }

    private void resizeToContent(String text) {
        int lines = 1 + (int) text.chars().filter(c -> c == '\n').count();
        setInputHeight(Math.max(MIN_ROWS, Math.min(MAX_ROWS, lines)));
    }

    private void setInputHeight(int rows) {
        double height = rows * ROW_HEIGHT + 12;
        input.setPrefHeight(height);
        input.setMinHeight(height);
    }
}
