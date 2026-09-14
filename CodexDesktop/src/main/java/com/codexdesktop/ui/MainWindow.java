package com.codexdesktop.ui;

import com.codexdesktop.codex.ApprovalRequest;
import com.codexdesktop.codex.CodexSessionListener;
import com.codexdesktop.codex.CodexSessionService;
import com.codexdesktop.codex.ConnectionState;
import com.codexdesktop.i18n.I18n;
import com.codexdesktop.model.AppSettings;
import com.codexdesktop.service.AttachmentService;
import com.codexdesktop.model.CodexThreadSummary;
import com.codexdesktop.model.ModelOption;
import com.codexdesktop.model.ProjectInfo;
import com.codexdesktop.service.GitService;
import com.codexdesktop.service.ProjectService;
import com.codexdesktop.service.SettingsService;
import com.codexdesktop.ui.conversation.ConversationView;
import com.codexdesktop.ui.markdown.MarkdownRenderer;
import com.codexdesktop.ui.office.AgentOfficeView;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.scene.Scene;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Application shell: sidebar, transcript, composer, status bar, and the settings/changes surfaces.
 *
 * <p>Also the coordinator for the app's few real workflows — pick a project, start or resume a
 * thread, send a message, stop a turn — keeping that logic out of the individual widgets.
 */
public final class MainWindow extends BorderPane implements CodexSessionListener {

    private static final Logger log = LoggerFactory.getLogger(MainWindow.class);
    private static final int THREAD_LIST_LIMIT = 40;
    private static final int HISTORY_TURN_LIMIT = 12;

    private final CodexSessionService session;
    private final ProjectService projectService;
    private final SettingsService settingsService;
    private final GitService gitService;
    private final AttachmentService attachmentService;

    private final Sidebar sidebar = new Sidebar();
    private final ConversationView conversation;
    private final Composer composer = new Composer();
    private final StatusBar statusBar = new StatusBar();
    private final ChangesPanel changesPanel = new ChangesPanel();
    private final SettingsView settingsView;
    private final StackPane centerStack = new StackPane();
    private final SplitPane rootSplit = new SplitPane();
    private final SplitPane centerSplit = new SplitPane();
    private final Label projectTitle = Ui.label(I18n.t("app.noProject"), "app-title");
    private final Label projectPath = Ui.label("", "project-path");
    private final javafx.scene.control.Button changesButton = Ui.button(I18n.t("changes.button"), "button", "subtle-outline");
    private final javafx.scene.control.Button modelButton = Ui.button(I18n.t("model.button"), "button", "subtle-outline");
    private final javafx.scene.control.Button compactButton = Ui.button(I18n.t("model.compact"), "button", "subtle-outline");
    private final javafx.scene.control.Button officeButton = Ui.button(I18n.t("office.button"), "button", "subtle-outline");
    private final AgentOfficeView officeView = new AgentOfficeView();

    private VBox conversationColumn;
    private Stage officeStage;
    private ProjectInfo activeProject;
    private List<CodexThreadSummary> lastThreadList = List.of();
    private boolean connecting;
    private Consumer<AppSettings> settingsApplier = settings -> { };

    public MainWindow(CodexSessionService session,
                      ProjectService projectService,
                      SettingsService settingsService,
                      GitService gitService,
                      AttachmentService attachmentService,
                      SettingsView settingsView,
                      MarkdownRenderer renderer) {
        this.session = session;
        this.projectService = projectService;
        this.settingsService = settingsService;
        this.gitService = gitService;
        this.attachmentService = attachmentService;
        this.settingsView = settingsView;
        this.conversation = new ConversationView(renderer);

        buildLayout();
        wireSidebar();
        wireComposer();
        wireSettings();

        session.addListener(conversation);
        session.addListener(officeView);
        session.addListener(this);
        officeView.setOnWorkerActivated(this::openThreadById);
        conversation.setApprovalHandler(session::resolveApproval);
        conversation.setDiffListener(this::onDiffUpdated);

        statusBar.setDistribution(settingsService.get().distribution());
        statusBar.setConnectionState(ConnectionState.DISCONNECTED);
        sidebar.bindProjects(projectService.projects());
    }

    /** Called after settings are saved so the app can re-apply theme, text size and language. */
    public void setSettingsApplier(Consumer<AppSettings> applier) {
        this.settingsApplier = applier;
    }

    /** Opens the most recently used project, if it still exists on disk. */
    public void restoreLastSession() {
        projectService.mostRecent().ifPresent(project -> {
            if (Files.isDirectory(Path.of(project.windowsPath()))) {
                selectProject(project);
            } else {
                conversation.appendNotice(I18n.t("error.projectMissing", project.windowsPath()), true);
            }
        });
    }

    /** Opens a specific project; used when the shell is rebuilt after a language change. */
    public void openProject(ProjectInfo project) {
        selectProject(project);
    }

    public void focusComposer() {
        composer.focusInput();
    }

    // ------------------------------------------------------------------- layout

    private void buildLayout() {
        changesButton.setOnAction(event -> toggleChanges());
        changesButton.setVisible(false);

        modelButton.setOnAction(event -> showModelMenu());
        modelButton.setVisible(false);
        Ui.attachTooltip(modelButton, I18n.t("model.tooltip"));

        compactButton.setOnAction(event -> compactThread());
        compactButton.setVisible(false);
        Ui.attachTooltip(compactButton, I18n.t("model.compactTooltip"));

        officeButton.setOnAction(event -> toggleOffice());
        Ui.attachTooltip(officeButton, I18n.t("office.tooltip"));

        VBox titleBox = new VBox(0, projectTitle, projectPath);
        HBox topBar = Ui.row(10, titleBox, Ui.hSpacer(), officeButton, modelButton, compactButton, changesButton);
        topBar.getStyleClass().add("top-bar");

        conversationColumn = new VBox(conversation, composer);
        VBox.setVgrow(conversation, Priority.ALWAYS);
        // A floor for the chat column so the office panel cannot squeeze it away, and vice versa.
        conversationColumn.setMinWidth(420);

        // The office sits beside the conversation rather than replacing it, so the top bar stays
        // reachable and the chat keeps working while you watch the agents.
        officeView.setVisible(false);
        officeView.setManaged(false);
        officeView.setOnClose(this::hideOffice);
        officeView.setOnToggleExpand(this::toggleOfficeExpanded);
        officeView.setOnPopOut(this::popOutOffice);

        // SplitPane instead of HBox so every boundary can be dragged. Panels are added and removed
        // as they are toggled; SplitPane has no notion of a hidden item.
        centerSplit.setOrientation(Orientation.HORIZONTAL);
        centerSplit.getItems().add(conversationColumn);
        centerSplit.getStyleClass().add("content-split");

        VBox mainColumn = new VBox(topBar, centerSplit);
        VBox.setVgrow(centerSplit, Priority.ALWAYS);

        centerStack.getChildren().addAll(mainColumn, settingsView);
        settingsView.setVisible(false);
        settingsView.setManaged(false);
        settingsView.setStyle("-fx-background-color: -c-bg;");

        // The sidebar is the second draggable boundary; SplitPane keeps both within the min widths
        // declared by the panes themselves.
        rootSplit.setOrientation(Orientation.HORIZONTAL);
        rootSplit.getItems().addAll(sidebar, centerStack);
        rootSplit.getStyleClass().add("content-split");
        SplitPane.setResizableWithParent(sidebar, false);

        setCenter(rootSplit);
        setBottom(statusBar);
    }

    private void wireSidebar() {
        sidebar.setOnAddProject(this::chooseProject);
        sidebar.setOnProjectSelected(this::selectProject);
        sidebar.setOnProjectRemoved(project -> {
            projectService.removeProject(project);
            if (activeProject != null && activeProject.id().equals(project.id())) {
                activeProject = null;
                conversation.reset();
                composer.readyProperty().set(false);
                projectTitle.setText(I18n.t("app.noProject"));
                projectPath.setText("");
                sidebar.setThreads(List.of());
            }
        });
        sidebar.setOnNewThread(this::startNewThread);
        sidebar.setOnThreadSelected(this::resumeThread);
        sidebar.setOnOpenSettings(this::openSettings);
    }

    private void wireComposer() {
        composer.setOnSend(this::sendMessage);
        composer.setOnStop(() -> session.interruptTurn().exceptionally(error -> {
            log.warn("Interrupt failed: {}", error.getMessage());
            return null;
        }));
        composer.setSlashCommands(buildSlashCommands());
        // @ mentions search the active project inside WSL through the app-server.
        composer.setFileSearch(query -> activeProject == null
                ? java.util.concurrent.CompletableFuture.completedFuture(List.<com.codexdesktop.model.FileMatch>of())
                : session.searchFiles(query, activeProject.wslPath(), 20));
        composer.setOnAttachRequested(this::chooseAttachments);
        composer.setOnFilesDropped(this::attachFiles);
        composer.setOnImagePasted(image -> {
            try {
                composer.addAttachment(attachmentService.fromImage(image));
            } catch (Exception e) {
                conversation.appendNotice(I18n.t("error.attachment", "clipboard", rootMessage(e)), true);
            }
        });
    }

    /**
     * The commands offered when a message starts with {@code /}.
     *
     * <p>Each one maps onto an action that already exists in the UI, so the palette is a keyboard
     * route to the same behaviour rather than a second implementation.
     */
    private List<SlashCommand> buildSlashCommands() {
        return List.of(
                new SlashCommand("new", I18n.t("slash.new"), this::startNewThread),
                new SlashCommand("model", I18n.t("slash.model"), this::showModelMenu),
                new SlashCommand("compact", I18n.t("slash.compact"), this::compactThread),
                new SlashCommand("diff", I18n.t("slash.diff"), this::toggleChanges),
                new SlashCommand("status", I18n.t("slash.status"), statusBar::showUsage),
                new SlashCommand("office", I18n.t("slash.office"), this::toggleOffice),
                new SlashCommand("settings", I18n.t("slash.settings"), this::openSettings),
                new SlashCommand("stop", I18n.t("slash.stop"), () -> session.interruptTurn()
                        .exceptionally(error -> null)),
                new SlashCommand("help", I18n.t("slash.help"), this::showSlashHelp));
    }

    private void showSlashHelp() {
        StringBuilder text = new StringBuilder(I18n.t("slash.helpTitle")).append('\n');
        for (SlashCommand command : buildSlashCommands()) {
            text.append('\n').append(command.display()).append("  -  ").append(command.description());
        }
        conversation.appendNotice(text.toString(), false);
    }

    private void chooseAttachments() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("composer.attach"));
        if (activeProject != null) {
            File initial = new File(activeProject.windowsPath());
            if (initial.isDirectory()) {
                chooser.setInitialDirectory(initial);
            }
        }
        Window window = getScene() == null ? null : getScene().getWindow();
        List<File> selected = chooser.showOpenMultipleDialog(window);
        if (selected != null && !selected.isEmpty()) {
            attachFiles(selected.stream().map(File::toPath).toList());
        }
    }

    /** Converts dropped or chosen Windows files into attachments with WSL paths. */
    private void attachFiles(List<Path> paths) {
        for (Path path : paths) {
            try {
                composer.addAttachment(attachmentService.fromFile(path));
            } catch (RuntimeException e) {
                conversation.appendNotice(
                        I18n.t("error.attachment", path.getFileName(), rootMessage(e)), true);
            }
        }
    }

    private void wireSettings() {
        settingsView.setOnClose(this::closeSettings);
        settingsView.setOnSave(updated -> {
            boolean languageChanged = !updated.language().equals(settingsService.get().language());
            settingsService.update(updated);
            statusBar.setDistribution(updated.distribution());
            session.client().setDebugWireLogging(updated.debugLogging());
            com.codexdesktop.CodexDesktopApplication.applyDebugLogLevel(updated.debugLogging());
            closeSettings();
            if (!languageChanged) {
                conversation.appendNotice(I18n.t("conversation.settingsSaved"), false);
            }
            // The shell is rebuilt when the language changes, so this runs last.
            settingsApplier.accept(updated);
        });
    }

    /** The project and thread the shell should reopen after a language-driven rebuild. */
    public ProjectInfo activeProject() {
        return activeProject;
    }

    // ------------------------------------------------------------------ workflows

    private void chooseProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18n.t("sidebar.addProject"));
        Window window = getScene() == null ? null : getScene().getWindow();
        File selected = chooser.showDialog(window);
        if (selected == null) {
            return;
        }
        try {
            ProjectInfo project = projectService.addProject(selected.toPath());
            selectProject(project);
        } catch (RuntimeException e) {
            conversation.appendNotice(I18n.t("error.addProject", rootMessage(e)), true);
        }
    }

    private void selectProject(ProjectInfo project) {
        this.activeProject = project;
        projectService.replace(project.touched());
        sidebar.setSelectedProject(project.id());
        projectTitle.setText(project.name());
        projectPath.setText(project.windowsPath() + "   \u2192   " + project.wslPath());
        conversation.reset();
        changesPanel.clear();
        changesButton.setVisible(false);
        composer.readyProperty().set(false);
        setThreadActionsVisible(false);
        refreshGitStatus();

        ensureConnected().thenRun(() -> Platform.runLater(() -> {
            startNewThread();
            refreshThreadList();
        })).exceptionally(error -> {
            Platform.runLater(() -> conversation.appendNotice(
                    I18n.t("error.startCodex", rootMessage(error)) + "\n" + I18n.t("error.startCodexHint"), true));
            return null;
        });
    }

    /** Starts the app-server once per app run; later projects reuse the same process. */
    private java.util.concurrent.CompletableFuture<Void> ensureConnected() {
        if (session.state() == ConnectionState.CONNECTED) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        if (connecting) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        connecting = true;
        String cwd = activeProject == null ? null : activeProject.wslPath();
        return session.connect(settingsService.get(), cwd).whenComplete((ignored, error) -> connecting = false);
    }

    private void startNewThread() {
        if (activeProject == null) {
            conversation.appendNotice(I18n.t("conversation.selectProjectFirst"), false);
            return;
        }
        if (session.state() != ConnectionState.CONNECTED) {
            ensureConnected().thenRun(() -> Platform.runLater(this::startNewThread));
            return;
        }
        conversation.reset();
        changesPanel.clear();
        session.startThread(activeProject.wslPath(), settingsService.get())
                .thenAccept(threadId -> Platform.runLater(() -> {
                    sidebar.setSelectedThread(threadId);
                    projectService.rememberLastThread(activeProject, threadId);
                    statusBar.setModel(session.modelName());
                    composer.readyProperty().set(true);
                    setThreadActionsVisible(true);
                    composer.focusInput();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> conversation.appendNotice(
                            I18n.t("error.startThread", rootMessage(error)), true));
                    return null;
                });
    }

    private void resumeThread(CodexThreadSummary thread) {
        if (activeProject == null) {
            return;
        }
        conversation.reset();
        changesPanel.clear();
        composer.readyProperty().set(false);
        sidebar.setSelectedThread(thread.id());

        session.resumeThread(thread.id(), activeProject.wslPath())
                .thenCompose(ignored -> session.listTurns(thread.id(), HISTORY_TURN_LIMIT))
                .thenAccept(turns -> Platform.runLater(() -> {
                    renderHistory(turns);
                    statusBar.setModel(session.modelName());
                    projectService.rememberLastThread(activeProject, thread.id());
                    composer.readyProperty().set(true);
                    setThreadActionsVisible(true);
                    composer.focusInput();
                    conversation.scrollToBottom();
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> conversation.appendNotice(
                            I18n.t("error.resumeThread", rootMessage(error)), true));
                    return null;
                });
    }

    /** thread/turns/list returns newest first; render oldest first. */
    private void renderHistory(List<JsonNode> turns) {
        for (int i = turns.size() - 1; i >= 0; i--) {
            JsonNode turn = turns.get(i);
            conversation.appendTurnDivider();
            turn.path("items").forEach(conversation::renderHistoryItem);
        }
    }

    private void sendMessage(String text, List<com.codexdesktop.model.Attachment> attachments) {
        conversation.appendTurnDivider();
        conversation.appendLocalUserMessage(text, attachments);
        composer.busyProperty().set(true);
        session.sendUserMessage(text, attachments).exceptionally(error -> {
            Platform.runLater(() -> {
                composer.busyProperty().set(false);
                conversation.appendNotice(I18n.t("error.sendMessage", rootMessage(error)), true);
            });
            return null;
        });
    }

    private void refreshThreadList() {
        if (activeProject == null || session.state() != ConnectionState.CONNECTED) {
            return;
        }
        sidebar.setThreadsLoading(true);
        session.listThreads(activeProject.wslPath(), THREAD_LIST_LIMIT)
                .thenAccept(threads -> Platform.runLater(() -> {
                    sidebar.setThreadsLoading(false);
                    sidebar.setThreads(threads);
                    sidebar.setSelectedThread(session.threadId());
                    lastThreadList = threads;
                    if (officeView.isVisible()) {
                        officeView.setThreads(threads, session.threadId());
                    }
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> sidebar.setThreadsLoading(false));
                    log.warn("Could not list threads: {}", rootMessage(error));
                    return null;
                });
    }

    private void refreshGitStatus() {
        ProjectInfo project = activeProject;
        if (project == null) {
            statusBar.setGitStatus(null);
            return;
        }
        Thread.ofVirtual().name("git-status").start(() -> {
            GitService.GitStatus status = gitService.status(project.wslPath());
            Platform.runLater(() -> statusBar.setGitStatus(status));
        });
    }

    private void onDiffUpdated(String diff) {
        changesPanel.setDiff(diff);
        boolean hasChanges = diff != null && !diff.isBlank();
        changesButton.setVisible(hasChanges);
        if (hasChanges && !changesPanel.isShown()) {
            changesButton.setText(I18n.t("changes.buttonPending"));
        }
    }

    /**
     * Model and reasoning-effort picker, populated from {@code model/list}.
     *
     * <p>The choice is applied as a {@code turn/start} override, which the protocol keeps for
     * subsequent turns, so switching model does not restart the thread.
     */
    private void showModelMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem loading = new MenuItem(I18n.t("sidebar.loading"));
        loading.setDisable(true);
        menu.getItems().add(loading);
        menu.show(modelButton, javafx.geometry.Side.BOTTOM, 0, 4);

        session.listModels().thenAccept(models -> Platform.runLater(() -> {
            menu.getItems().clear();
            if (models.isEmpty()) {
                MenuItem empty = new MenuItem(I18n.t("model.none"));
                empty.setDisable(true);
                menu.getItems().add(empty);
                return;
            }
            String activeModel = session.threadConfig().model();
            for (ModelOption model : models) {
                if (model.supportedEfforts().isEmpty()) {
                    menu.getItems().add(modelItem(model, "", activeModel));
                    continue;
                }
                Menu submenu = new Menu(label(model, activeModel));
                for (String effort : model.supportedEfforts()) {
                    submenu.getItems().add(effortItem(model, effort));
                }
                menu.getItems().add(submenu);
            }
        })).exceptionally(error -> {
            Platform.runLater(() -> {
                menu.hide();
                conversation.appendNotice(I18n.t("model.listFailed", rootMessage(error)), true);
            });
            return null;
        });
    }

    private MenuItem modelItem(ModelOption model, String effort, String activeModel) {
        MenuItem item = new MenuItem(label(model, activeModel));
        item.setOnAction(event -> applyModel(model, effort));
        return item;
    }

    private MenuItem effortItem(ModelOption model, String effort) {
        MenuItem item = new MenuItem(effort
                + (effort.equals(model.defaultEffort()) ? "  \u00b7 " + I18n.t("model.default") : ""));
        item.setOnAction(event -> applyModel(model, effort));
        return item;
    }

    private void applyModel(ModelOption model, String effort) {
        session.selectModel(model.id(), effort);
        String suffix = effort.isBlank() ? "" : " \u00b7 " + effort;
        modelButton.setText(model.displayName() + suffix);
        conversation.appendNotice(I18n.t("model.selected", model.displayName() + suffix), false);
    }

    private static String label(ModelOption model, String activeModel) {
        String marker = model.id().equals(activeModel) ? "\u2713  " : "";
        return marker + model.displayName();
    }

    /** Asks Codex to compact the thread context, mirroring the CLI's compact command. */
    private void compactThread() {
        compactButton.setDisable(true);
        session.compactThread()
                .thenRun(() -> Platform.runLater(() -> {
                    compactButton.setDisable(false);
                    conversation.appendNotice(I18n.t("model.compactStarted"), false);
                }))
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        compactButton.setDisable(false);
                        conversation.appendNotice(I18n.t("model.compactFailed", rootMessage(error)), true);
                    });
                    return null;
                });
    }

    /** Model and compact actions only make sense once a thread exists. */
    private void setThreadActionsVisible(boolean visible) {
        modelButton.setVisible(visible);
        compactButton.setVisible(visible);
    }

    /** Opens the thread whose agent was clicked in the office; the office stays open beside it. */
    private void openThreadById(String threadId) {
        lastThreadList.stream()
                .filter(thread -> thread.id().equals(threadId))
                .findFirst()
                .ifPresent(this::resumeThread);
    }

    /**
     * Adds a side panel to the centre split, keeping the conversation first.
     *
     * <p>SplitPane has no hidden state, so panels join and leave the item list as they are toggled.
     * The divider is placed so the panel opens near its preferred width rather than at 50%.
     */
    private void addSplitItem(javafx.scene.layout.Region panel) {
        if (centerSplit.getItems().contains(panel)) {
            return;
        }
        centerSplit.getItems().add(panel);
        double total = Math.max(1, centerSplit.getWidth());
        double preferred = panel.getPrefWidth() > 0 ? panel.getPrefWidth() : 420;
        double position = Math.max(0.35, Math.min(0.85, 1 - preferred / total));
        int dividerIndex = centerSplit.getItems().size() - 2;
        if (dividerIndex >= 0) {
            centerSplit.setDividerPosition(dividerIndex, position);
        }
    }

    /** Shows or hides the changed-files panel next to the conversation. */
    private void toggleChanges() {
        if (changesPanel.isShown()) {
            centerSplit.getItems().remove(changesPanel);
            changesPanel.setVisibleManaged(false);
        } else {
            changesPanel.setVisibleManaged(true);
            addSplitItem(changesPanel);
        }
    }

    /** Shows or hides the office panel next to the conversation. */
    private void toggleOffice() {
        if (officeView.isVisible()) {
            hideOffice();
            return;
        }
        officeView.setVisible(true);
        officeView.setManaged(true);
        addSplitItem(officeView);
        Ui.toggleClass(officeButton, "selected-toggle", true);
        officeView.setThreads(lastThreadList, session.threadId());
    }

    /**
     * Grows the office to fill the centre area, hiding the chat, and back again.
     *
     * <p>The renderer then picks a bigger integer zoom by itself, so the room gets larger instead
     * of being stretched.
     */
    private void toggleOfficeExpanded() {
        boolean expand = !officeView.isExpanded();
        officeView.setExpanded(expand);
        conversationColumn.setVisible(!expand);
        conversationColumn.setManaged(!expand);
    }

    /**
     * Detaches the office into its own window.
     *
     * <p>Useful on a second monitor: the room gets as much space as the window is given, and the
     * main window goes back to being just the chat. Closing the detached window docks it again.
     */
    private void popOutOffice() {
        if (officeStage != null) {
            officeStage.requestFocus();
            return;
        }
        if (officeView.isExpanded()) {
            toggleOfficeExpanded();
        }
        centerSplit.getItems().remove(officeView);

        officeView.setDetached(true);
        officeView.setVisible(true);
        officeView.setManaged(true);

        StackPane host = new StackPane(officeView);
        host.getStyleClass().add("root");
        // 16 tiles * 16 px * 3 = 768 for the room, plus the header and footer: the window opens at
        // an exact 3x fit instead of landing on 2x with a wide empty border.
        Scene scene = new Scene(host, 792, 764);
        if (getScene() != null) {
            scene.getStylesheets().setAll(getScene().getStylesheets());
            host.setStyle(getScene().getRoot().getStyle());
        }

        officeStage = new Stage();
        officeStage.setTitle(I18n.t("office.title"));
        officeStage.setScene(scene);
        officeStage.setMinWidth(540);
        officeStage.setMinHeight(600);
        if (getScene() != null && getScene().getWindow() instanceof Stage owner) {
            officeStage.getIcons().setAll(owner.getIcons());
        }
        officeStage.setOnHidden(event -> dockOffice());
        officeStage.show();

        Ui.toggleClass(officeButton, "selected-toggle", true);
    }

    /** Returns a detached office to its slot beside the conversation. */
    private void dockOffice() {
        Stage stage = officeStage;
        officeStage = null;
        if (stage != null && stage.isShowing()) {
            stage.close();
        }
        if (officeView.getParent() instanceof StackPane host) {
            host.getChildren().remove(officeView);
        }
        officeView.setDetached(false);
        officeView.setVisible(false);
        officeView.setManaged(false);
        Ui.toggleClass(officeButton, "selected-toggle", false);
    }

    /**
     * Closes windows this shell opened.
     *
     * <p>Without this the detached office would keep the JavaFX runtime alive after the main window
     * was closed, and the process would linger.
     */
    public void closeAuxiliaryWindows() {
        Stage stage = officeStage;
        officeStage = null;
        if (stage != null) {
            stage.setOnHidden(null);
            stage.close();
        }
    }

    private void hideOffice() {
        if (officeStage != null) {
            dockOffice();
            return;
        }
        if (officeView.isExpanded()) {
            toggleOfficeExpanded();
        }
        centerSplit.getItems().remove(officeView);
        officeView.setVisible(false);
        officeView.setManaged(false);
        Ui.toggleClass(officeButton, "selected-toggle", false);
        composer.focusInput();
    }

    private void openSettings() {
        settingsView.load(settingsService.get());
        settingsView.setVisible(true);
        settingsView.setManaged(true);
    }

    private void closeSettings() {
        settingsView.setVisible(false);
        settingsView.setManaged(false);
    }

    // -------------------------------------------------------------- session events

    @Override
    public void onConnectionStateChanged(ConnectionState state, String detail) {
        statusBar.setConnectionState(state);
        if (state == ConnectionState.CONNECTED) {
            session.refreshRateLimits();
        }
        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.FAILED) {
            composer.readyProperty().set(false);
            composer.busyProperty().set(false);
        }
    }

    @Override
    public void onUsageChanged(com.codexdesktop.model.UsageSnapshot usage) {
        statusBar.setUsage(usage);
    }

    @Override
    public void onThreadConfigured(com.codexdesktop.model.ThreadConfig config) {
        statusBar.setThreadConfig(config);
        if (!config.model().isBlank()) {
            String effort = config.reasoningEffort();
            modelButton.setText(config.model() + (effort.isBlank() ? "" : " \u00b7 " + effort));
        }
    }

    @Override
    public void onTurnCompleted(String turnId, String status, JsonNode turn) {
        composer.busyProperty().set(false);
        if ("interrupted".equals(status)) {
            conversation.appendNotice(I18n.t("conversation.turnStopped"), false);
        } else if ("failed".equals(status)) {
            String message = turn.path("error").path("message").asText(I18n.t("error.turnFailed"));
            conversation.appendNotice(message, true);
        }
        refreshGitStatus();
        refreshThreadList();
    }

    @Override
    public void onTurnStarted(String turnId) {
        composer.busyProperty().set(true);
    }

    @Override
    public void onApprovalRequested(ApprovalRequest request) {
        conversation.scrollToBottom();
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
