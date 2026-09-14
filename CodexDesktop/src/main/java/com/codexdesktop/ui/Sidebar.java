package com.codexdesktop.ui;

import com.codexdesktop.i18n.I18n;
import com.codexdesktop.model.CodexThreadSummary;
import com.codexdesktop.model.ProjectInfo;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Left rail: new-thread action, projects, threads for the selected project, settings.
 *
 * <p>Rows are plain styled {@link HBox}es rather than {@code ListView} cells: the lists are short,
 * this keeps full control over the visuals, and it avoids the default list-cell look.
 */
public final class Sidebar extends VBox {

    private final VBox projectList = new VBox(2);
    private final VBox threadList = new VBox(2);
    private final Label threadsEmpty = Ui.label(I18n.t("sidebar.noThreads"), "caption");

    private Consumer<ProjectInfo> onProjectSelected = project -> { };
    private Consumer<ProjectInfo> onProjectRemoved = project -> { };
    private Consumer<CodexThreadSummary> onThreadSelected = thread -> { };
    private Runnable onNewThread = () -> { };
    private Runnable onAddProject = () -> { };
    private Runnable onOpenSettings = () -> { };

    private String selectedProjectId = "";
    private String selectedThreadId = "";

    public Sidebar() {
        getStyleClass().add("sidebar");

        var newThreadButton = Ui.button(I18n.t("sidebar.newThread"), "button", "subtle-outline");
        newThreadButton.setMaxWidth(Double.MAX_VALUE);
        newThreadButton.setOnAction(event -> onNewThread.run());
        VBox top = new VBox(newThreadButton);
        top.setPadding(new Insets(10, 10, 4, 10));

        HBox projectsHeader = header(I18n.t("sidebar.projects"), "\uff0b", I18n.t("sidebar.addProject"), () -> onAddProject.run());
        HBox threadsHeader = header(I18n.t("sidebar.threads"), null, null, null);

        VBox content = new VBox(projectsHeader, projectList, threadsHeader, threadList, threadsEmpty);
        content.setPadding(new Insets(0, 6, 10, 6));
        threadsEmpty.setPadding(new Insets(2, 10, 2, 10));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("sidebar-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);

        var settingsButton = Ui.button(I18n.t("sidebar.settings"), "button");
        settingsButton.setMaxWidth(Double.MAX_VALUE);
        settingsButton.setOnAction(event -> onOpenSettings.run());
        VBox footer = new VBox(settingsButton);
        footer.getStyleClass().add("sidebar-footer");

        getChildren().addAll(top, scroll, footer);
    }

    public void setOnProjectSelected(Consumer<ProjectInfo> handler) {
        this.onProjectSelected = handler;
    }

    public void setOnProjectRemoved(Consumer<ProjectInfo> handler) {
        this.onProjectRemoved = handler;
    }

    public void setOnThreadSelected(Consumer<CodexThreadSummary> handler) {
        this.onThreadSelected = handler;
    }

    public void setOnNewThread(Runnable handler) {
        this.onNewThread = handler;
    }

    public void setOnAddProject(Runnable handler) {
        this.onAddProject = handler;
    }

    public void setOnOpenSettings(Runnable handler) {
        this.onOpenSettings = handler;
    }

    public void bindProjects(ObservableList<ProjectInfo> projects) {
        projects.addListener((javafx.collections.ListChangeListener<ProjectInfo>) change -> renderProjects(projects));
        renderProjects(projects);
    }

    public void setSelectedProject(String projectId) {
        this.selectedProjectId = projectId == null ? "" : projectId;
        for (var node : projectList.getChildren()) {
            Ui.toggleClass(node, "selected", selectedProjectId.equals(node.getUserData()));
        }
    }

    public void setSelectedThread(String threadId) {
        this.selectedThreadId = threadId == null ? "" : threadId;
        for (var node : threadList.getChildren()) {
            Ui.toggleClass(node, "selected", selectedThreadId.equals(node.getUserData()));
        }
    }

    /** Replaces the thread list, inserting day-group labels. */
    public void setThreads(List<CodexThreadSummary> threads) {
        threadList.getChildren().clear();
        threadsEmpty.setVisible(threads.isEmpty());
        threadsEmpty.setManaged(threads.isEmpty());
        String currentGroup = "";
        for (CodexThreadSummary thread : threads) {
            String group = thread.dayGroup();
            if (!group.equals(currentGroup)) {
                currentGroup = group;
                Label groupLabel = Ui.label(group, "caption");
                groupLabel.setPadding(new Insets(8, 10, 2, 10));
                threadList.getChildren().add(groupLabel);
            }
            threadList.getChildren().add(threadRow(thread));
        }
        setSelectedThread(selectedThreadId);
    }

    public void setThreadsLoading(boolean loading) {
        if (loading) {
            threadsEmpty.setText(I18n.t("sidebar.loading"));
            threadsEmpty.setVisible(true);
            threadsEmpty.setManaged(true);
        } else {
            threadsEmpty.setText(I18n.t("sidebar.noThreads"));
        }
    }

    private void renderProjects(ObservableList<ProjectInfo> projects) {
        projectList.getChildren().clear();
        if (projects.isEmpty()) {
            Label hint = Ui.label(I18n.t("sidebar.noProjects"), "caption");
            hint.setPadding(new Insets(2, 10, 2, 10));
            projectList.getChildren().add(hint);
            return;
        }
        for (ProjectInfo project : projects) {
            projectList.getChildren().add(projectRow(project));
        }
        setSelectedProject(selectedProjectId);
    }

    private HBox projectRow(ProjectInfo project) {
        Label name = Ui.label(project.name(), "label");
        Label path = Ui.label(Ui.ellipsizeMiddle(project.windowsPath(), 34), "sub");
        VBox text = new VBox(1, name, path);
        HBox row = new HBox(text);
        row.getStyleClass().add("sidebar-item");
        row.setUserData(project.id());
        Ui.attachTooltip(row, project.windowsPath() + "\n" + project.wslPath());
        row.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                onProjectSelected.accept(project);
            }
        });

        var remove = Ui.iconButton("\u00d7", I18n.t("sidebar.removeProject"));
        remove.setVisible(false);
        remove.setOnAction(event -> onProjectRemoved.accept(project));
        Region spacer = Ui.hSpacer();
        row.getChildren().addAll(spacer, remove);
        row.setOnMouseEntered(event -> remove.setVisible(true));
        row.setOnMouseExited(event -> remove.setVisible(false));
        return row;
    }

    private HBox threadRow(CodexThreadSummary thread) {
        Label title = Ui.label(thread.title(), "label");
        title.setWrapText(false);
        HBox row = new HBox(title);
        row.getStyleClass().add("sidebar-item");
        row.setUserData(thread.id());
        Ui.attachTooltip(row, thread.title());
        row.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                onThreadSelected.accept(thread);
            }
        });
        return row;
    }

    private HBox header(String text, String actionGlyph, String actionTooltip, Runnable action) {
        Label label = Ui.label(text, "section-label");
        HBox row = new HBox(label);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        if (actionGlyph != null) {
            var button = Ui.iconButton(actionGlyph, actionTooltip);
            button.setOnAction(event -> action.run());
            row.getChildren().addAll(Ui.hSpacer(), button);
        }
        return row;
    }
}
