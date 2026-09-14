package com.codexdesktop.ui;

import com.codexdesktop.codex.ApprovalRequest;
import com.codexdesktop.codex.ConnectionState;
import com.codexdesktop.i18n.I18n;
import com.codexdesktop.model.Attachment;
import com.codexdesktop.model.ThreadConfig;
import com.codexdesktop.model.UsageSnapshot;
import com.codexdesktop.service.GitService;
import com.codexdesktop.ui.conversation.ConversationView;
import com.codexdesktop.ui.markdown.MarkdownRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Renders the real widgets to PNG files without opening a window, so the visual design can be
 * reviewed (and re-reviewed after CSS changes) without a desktop session.
 *
 * <p>Opt-in, like the integration test:
 * {@code mvn test -Dgroups=preview -Dsurefire.excludedGroups=none}
 * Output goes to {@code target/ui-preview}.
 */
@Tag("preview")
class UiPreviewTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 840;

    private static Path outputDirectory;
    private com.codexdesktop.ui.office.AgentOfficeView officeToRender;

    @BeforeAll
    static void setUp() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        assertTrue(started.await(60, TimeUnit.SECONDS), "JavaFX toolkit did not start");
        FontLoader.loadBundledFonts();
        outputDirectory = Path.of("target", "ui-preview");
        Files.createDirectories(outputDirectory);
    }

    @AfterAll
    static void tearDown() {
        Platform.exit();
    }

    @Test
    void rendersConversationAndSettings() throws Exception {
        I18n.setLanguage(I18n.LANGUAGE_KOREAN);
        render("conversation-ko", this::buildConversationShell);
        render("settings-ko", this::buildSettingsShell);
        render("usage-ko", this::buildUsageShell);
        render("office-ko", this::buildOfficeShell);
        renderSized("office-detached-ko", this::buildDetachedOfficeShell, 792, 764);

        I18n.setLanguage(I18n.LANGUAGE_ENGLISH);
        render("conversation-en", this::buildConversationShell);

        renderModelMenu();

        try (var files = Files.list(outputDirectory)) {
            List<Path> written = files.toList();
            assertTrue(written.size() >= 5, "expected preview images, got " + written);
            written.forEach(file -> System.out.println("[PREVIEW] " + file.toAbsolutePath()));
        }
    }

    /**
     * Renders the office as a standalone window would show it, at the size the pop-out opens with.
     */
    private javafx.scene.Parent buildDetachedOfficeShell() {
        com.codexdesktop.ui.office.AgentOfficeView office =
                new com.codexdesktop.ui.office.AgentOfficeView();
        office.setDetached(true);
        long now = System.currentTimeMillis() / 1000;
        List<com.codexdesktop.model.CodexThreadSummary> threads = new java.util.ArrayList<>();
        String[] titles = {"SearchService 페이징", "스탬프 적립 버그", "리팩터링 검토", "테스트 추가",
                "문서 정리", "쿼리 튜닝"};
        for (int i = 0; i < titles.length; i++) {
            threads.add(new com.codexdesktop.model.CodexThreadSummary(
                    "d" + i, titles[i], "/mnt/c/dev", now - i * 600L, "main"));
        }
        office.setThreads(threads, "d0");
        office.onTurnStarted("turn-1");
        office.onActivityChanged(I18n.t("activity.editing"));
        officeToRender = office;
        javafx.scene.layout.StackPane host = new javafx.scene.layout.StackPane(office);
        host.getStyleClass().add("root");
        return host;
    }

    /**
     * Menus render in their own popup window, so a scene snapshot of the shell cannot show them.
     * This shows a real {@link javafx.scene.control.ContextMenu} off-screen, snapshots it, and
     * hides it again — enough to confirm the styling actually reaches menu items.
     */
    private void renderModelMenu() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            javafx.stage.Stage owner = null;
            javafx.scene.control.ContextMenu menu = null;
            try {
                I18n.setLanguage(I18n.LANGUAGE_KOREAN);
                javafx.scene.layout.StackPane anchor = new javafx.scene.layout.StackPane();
                anchor.getStyleClass().add("root");
                Scene ownerScene = new Scene(anchor, 200, 80);
                ownerScene.getStylesheets().setAll(
                        stylesheet("/css/base.css"), stylesheet("/css/dark.css"));
                owner = new javafx.stage.Stage();
                owner.setScene(ownerScene);
                // Parked off-screen so running the preview does not flash a window at the user.
                owner.setX(-3000);
                owner.setY(-3000);
                owner.setOpacity(0);
                owner.show();

                menu = new javafx.scene.control.ContextMenu();
                String[] models = {"GPT-6-Astra", "GPT-5.6-Sol", "GPT-5.6-Terra", "GPT-5.6-Luna"};
                for (int i = 0; i < models.length; i++) {
                    javafx.scene.control.Menu entry =
                            new javafx.scene.control.Menu((i == 1 ? "\u2713  " : "") + models[i]);
                    for (String effort : new String[]{"low", "medium", "high"}) {
                        entry.getItems().add(new javafx.scene.control.MenuItem(
                                effort + ("medium".equals(effort) ? "  \u00b7 " + I18n.t("model.default") : "")));
                    }
                    menu.getItems().add(entry);
                }
                menu.getItems().add(new javafx.scene.control.MenuItem("GPT-5.5"));
                menu.show(anchor, -2900, -2900);
                menu.getScene().getRoot().applyCss();
                menu.getScene().getRoot().layout();
                writePng(menu.getScene().snapshot(null), outputDirectory.resolve("menu-ko.png"));

                // Same treatment for the ComboBox dropdown: it is a popup too, so its cells were
                // also missing the palette.
                javafx.scene.control.ComboBox<String> combo = new javafx.scene.control.ComboBox<>();
                combo.getItems().setAll(I18n.t("settings.inherit"), "read-only",
                        "workspace-write", "danger-full-access");
                combo.setValue("workspace-write");
                anchor.getChildren().add(combo);
                anchor.applyCss();
                anchor.layout();
                combo.show();
                if (combo.getScene() != null) {
                    var popupRoot = combo.getSkin() instanceof javafx.scene.control.skin.ComboBoxListViewSkin<?> skin
                            ? skin.getPopupContent() : null;
                    if (popupRoot != null && popupRoot.getScene() != null) {
                        popupRoot.getScene().getRoot().applyCss();
                        popupRoot.getScene().getRoot().layout();
                        writePng(popupRoot.getScene().snapshot(null),
                                outputDirectory.resolve("combo-ko.png"));
                    }
                }
                combo.hide();

                // The slash palette is a Popup as well.
                SlashCommandPopup palette = new SlashCommandPopup();
                palette.applyStylesheets(java.util.List.of(
                        stylesheet("/css/base.css"), stylesheet("/css/dark.css")),
                        "-fx-font-size: 13.5px;");
                palette.showFor(anchor, java.util.List.of(
                        new SlashCommand("new", I18n.t("slash.new"), () -> { }),
                        new SlashCommand("model", I18n.t("slash.model"), () -> { }),
                        new SlashCommand("compact", I18n.t("slash.compact"), () -> { }),
                        new SlashCommand("diff", I18n.t("slash.diff"), () -> { }),
                        new SlashCommand("status", I18n.t("slash.status"), () -> { }),
                        new SlashCommand("office", I18n.t("slash.office"), () -> { }),
                        new SlashCommand("settings", I18n.t("slash.settings"), () -> { }),
                        new SlashCommand("stop", I18n.t("slash.stop"), () -> { })), "");
                if (palette.isShowing()) {
                    palette.getScene().getRoot().applyCss();
                    palette.getScene().getRoot().layout();
                    writePng(palette.getScene().snapshot(null),
                            outputDirectory.resolve("slash-ko.png"));
                }
                palette.hide();

                // The @ file picker, populated with a shape identical to fuzzyFileSearch results.
                MentionPopup mentions = new MentionPopup();
                mentions.applyStylesheets(java.util.List.of(
                        stylesheet("/css/base.css"), stylesheet("/css/dark.css")),
                        "-fx-font-size: 13.5px;");
                String root = "/mnt/c/dev/VisitSeoul";
                mentions.showFor(anchor, java.util.List.of(
                        new com.codexdesktop.model.FileMatch(root,
                                "src/main/java/SearchService.java", "SearchService.java", false, 209),
                        new com.codexdesktop.model.FileMatch(root,
                                "src/main/java/SearchController.java", "SearchController.java", false, 187),
                        new com.codexdesktop.model.FileMatch(root,
                                "src/test/java/SearchServiceTest.java", "SearchServiceTest.java", false, 154),
                        new com.codexdesktop.model.FileMatch(root,
                                "src/main/resources/search", "search", true, 132),
                        new com.codexdesktop.model.FileMatch(root, "pom.xml", "pom.xml", false, 84)));
                if (mentions.isShowing()) {
                    mentions.getScene().getRoot().applyCss();
                    mentions.getScene().getRoot().layout();
                    writePng(mentions.getScene().snapshot(null),
                            outputDirectory.resolve("mention-ko.png"));
                }
                mentions.hide();
            } catch (Throwable t) {
                failure[0] = t;
            } finally {
                if (menu != null) {
                    menu.hide();
                }
                if (owner != null) {
                    owner.close();
                }
                done.countDown();
            }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "model menu render timed out");
        if (failure[0] != null) {
            throw new AssertionError("model menu render failed", failure[0]);
        }
    }

    /**
     * Renders the office in the layout it actually ships in: a panel beside the conversation, so
     * the chat stays usable and the top bar (with the toggle back) is still there.
     */
    private javafx.scene.Parent buildOfficeShell() {
        javafx.scene.Parent shell = buildConversationShell();
        com.codexdesktop.ui.office.AgentOfficeView office =
                new com.codexdesktop.ui.office.AgentOfficeView();
        long now = System.currentTimeMillis() / 1000;
        List<com.codexdesktop.model.CodexThreadSummary> threads = new java.util.ArrayList<>();
        String[] titles = {"SearchService 페이징", "스탬프 적립 버그", "리팩터링 검토", "테스트 추가",
                "문서 정리", "쿼리 튜닝", "빌드 스크립트", "로그 정리"};
        for (int i = 0; i < titles.length; i++) {
            threads.add(new com.codexdesktop.model.CodexThreadSummary(
                    "t" + (i + 1), titles[i], "/mnt/c/dev", now - i * 3600L, "main"));
        }
        office.setThreads(threads, "t1");
        office.onTurnStarted("turn-1");
        office.onActivityChanged(I18n.t("activity.runningCommand"));
        officeToRender = office;

        // Drop it into the centre row of the shell, exactly like MainWindow does.
        if (shell instanceof BorderPane pane && pane.getCenter() instanceof VBox centre
                && centre.getChildren().size() > 1
                && centre.getChildren().get(1) instanceof javafx.scene.layout.HBox row) {
            row.getChildren().add(office);
        }
        return shell;
    }

    private interface ShellBuilder {
        javafx.scene.Parent build();
    }

    /** Lays the node tree out off-screen and snapshots it. */
    private void render(String name, ShellBuilder builder) throws Exception {
        renderSized(name, builder, WIDTH, HEIGHT);
    }

    /** Same as {@link #render} but at an explicit size, for windows that are not the main shell. */
    private void renderSized(String name, ShellBuilder builder, int width, int height)
            throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                javafx.scene.Parent root = builder.build();
                Scene scene = new Scene(root, width, height);
                scene.getStylesheets().setAll(
                        stylesheet("/css/base.css"), stylesheet("/css/dark.css"));
                root.setStyle("-fx-font-size: 13.5px;");
                // A scene that was never shown still needs an explicit CSS + layout pass.
                root.applyCss();
                root.layout();
                if (officeToRender != null) {
                    // Long enough for everyone to reach their desk and start typing; a snapshot
                    // happens outside the JavaFX pulse, so the loop is driven by hand here.
                    officeToRender.renderFrameNow(3.0);
                    officeToRender = null;
                }
                WritableImage image = scene.snapshot(null);
                writePng(image, outputDirectory.resolve(name + ".png"));
            } catch (Throwable t) {
                failure[0] = t;
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(60, TimeUnit.SECONDS), "render of " + name + " timed out");
        if (failure[0] != null) {
            throw new AssertionError("render of " + name + " failed", failure[0]);
        }
    }

    private javafx.scene.Parent buildConversationShell() {
        ObjectMapper mapper = new ObjectMapper();
        ConversationView conversation = new ConversationView(new MarkdownRenderer(url -> { }));

        conversation.appendLocalUserMessage("SearchService 페이징 로직 확인해줘",
                List.of(new Attachment("SearchService.java", Path.of("C:/dev/SearchService.java"),
                        "/mnt/c/dev/SearchService.java", false, 8342)));

        conversation.renderHistoryItem(json(mapper, """
                {"type":"reasoning","id":"r1",
                 "summary":["**페이징 파라미터 확인** limit 과 pagelength 사용처를 비교하고 있습니다."],
                 "content":[]}"""));

        conversation.renderHistoryItem(json(mapper, """
                {"type":"agentMessage","id":"m1","phase":"commentary",
                 "text":"프로젝트 구조를 먼저 살펴보겠습니다."}"""));

        conversation.renderHistoryItem(json(mapper, """
                {"type":"commandExecution","id":"c1","command":"/bin/bash -lc 'rg -n \\"limit\\" src'",
                 "cwd":"/mnt/c/dev/VisitSeoul","status":"completed","exitCode":0,"durationMs":420,
                 "commandActions":[{"type":"search","command":"rg -n limit src","query":"limit","path":"src"}],
                 "aggregatedOutput":"src/main/java/SearchService.java:42:  int limit = 12;\\n"}"""));

        conversation.renderHistoryItem(json(mapper, """
                {"type":"commandExecution","id":"c2","command":"/bin/bash -lc 'mvn -q test'",
                 "cwd":"/mnt/c/dev/VisitSeoul","status":"failed","exitCode":1,"durationMs":18240,
                 "commandActions":[{"type":"unknown","command":"mvn -q test"}],
                 "aggregatedOutput":"[ERROR] Tests run: 4, Failures: 1\\n"}"""));

        conversation.renderHistoryItem(json(mapper, """
                {"type":"fileChange","id":"f1","status":"completed","changes":[
                  {"path":"/mnt/c/dev/VisitSeoul/src/main/java/SearchService.java",
                   "kind":{"type":"update","move_path":null},
                   "diff":"@@ -39,7 +39,7 @@\\n     public Page search(String q) {\\n-        int limit = 12;\\n+        int pageLength = 12;\\n         return repository.find(q);\\n"}]}"""));

        conversation.renderHistoryItem(json(mapper, """
                {"type":"agentMessage","id":"m2","phase":"final_answer",
                 "text":"`SearchService`의 페이징은 두 군데에서 어긋나 있었습니다.\\n\\n### 변경 내용\\n\\n- `limit` 파라미터를 `pageLength`로 정리했습니다\\n- 호출부 **2곳**을 함께 수정했습니다\\n\\n```java\\nPage<Item> page = service.search(query, pageLength);\\n```\\n\\n남은 확인 사항은 [문서](https://example.com)에 정리해 두었습니다."}"""));

        conversation.onApprovalRequested(new ApprovalRequest(
                mapper.getNodeFactory().numberNode(7), ApprovalRequest.Kind.COMMAND,
                "t1", "u1", "exec-1", "mvn clean package -DskipTests",
                "/mnt/c/dev/VisitSeoul", "네트워크 접근이 필요합니다",
                List.of(ApprovalRequest.ACCEPT, ApprovalRequest.ACCEPT_FOR_SESSION, ApprovalRequest.DECLINE)));
        conversation.onActivityChanged(I18n.t("activity.runningCommand"));

        Composer composer = new Composer();
        composer.readyProperty().set(true);

        StatusBar statusBar = new StatusBar();
        statusBar.setDistribution("Ubuntu-24.04");
        statusBar.setConnectionState(ConnectionState.CONNECTED);
        statusBar.setThreadConfig(new ThreadConfig("gpt-6-astra", "openai", "medium",
                "on-request", "workspace-write", "/mnt/c/dev/VisitSeoul"));
        statusBar.setUsage(sampleUsage(mapper));
        statusBar.setGitStatus(new GitService.GitStatus(true, "main", 3, 12, 4));

        Sidebar sidebar = new Sidebar();
        sidebar.bindProjects(javafx.collections.FXCollections.observableArrayList(
                new com.codexdesktop.model.ProjectInfo("1", "VisitSeoul",
                        "C:\\Users\\USER\\dev\\VisitSeoul", "/mnt/c/Users/USER/dev/VisitSeoul", null, 2),
                new com.codexdesktop.model.ProjectInfo("2", "BearWorld",
                        "C:\\Users\\USER\\dev\\BearWorld", "/mnt/c/Users/USER/dev/BearWorld", null, 1)));
        sidebar.setSelectedProject("1");
        sidebar.setThreads(List.of(
                new com.codexdesktop.model.CodexThreadSummary("t1", "SearchService 페이징 확인",
                        "/mnt/c/dev/VisitSeoul", System.currentTimeMillis() / 1000, "main"),
                new com.codexdesktop.model.CodexThreadSummary("t2", "스탬프 적립 버그 추적",
                        "/mnt/c/dev/VisitSeoul", System.currentTimeMillis() / 1000 - 90_000, "main")));
        sidebar.setSelectedThread("t1");

        javafx.scene.control.Label title = Ui.label("VisitSeoul", "app-title");
        javafx.scene.control.Label path = Ui.label(
                "C:\\Users\\USER\\dev\\VisitSeoul   \u2192   /mnt/c/Users/USER/dev/VisitSeoul", "project-path");
        var modelButton = Ui.button("gpt-6-astra \u00b7 medium", "button");
        var compactButton = Ui.button(I18n.t("model.compact"), "button");
        var changesButton = Ui.button(I18n.t("changes.buttonPending"), "button", "subtle-outline");
        var topBar = Ui.row(10, new VBox(0, title, path), Ui.hSpacer(),
                modelButton, compactButton, changesButton);
        topBar.getStyleClass().add("top-bar");

        VBox conversationColumn = new VBox(conversation, composer);
        VBox.setVgrow(conversation, Priority.ALWAYS);
        javafx.scene.layout.HBox centerRow = new javafx.scene.layout.HBox(conversationColumn);
        javafx.scene.layout.HBox.setHgrow(conversationColumn, Priority.ALWAYS);

        VBox center = new VBox(topBar, centerRow);
        VBox.setVgrow(centerRow, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setLeft(sidebar);
        root.setCenter(center);
        root.setBottom(statusBar);
        return root;
    }

    private javafx.scene.Parent buildSettingsShell() {
        SettingsView view = new SettingsView(new com.codexdesktop.service.DiagnosticsService());
        view.load(com.codexdesktop.model.AppSettings.defaults());
        view.getStyleClass().add("root");
        view.setStyle("-fx-background-color: -c-bg;");
        return view;
    }

    private javafx.scene.Parent buildUsageShell() {
        ObjectMapper mapper = new ObjectMapper();
        UsagePopup popup = new UsagePopup();
        popup.update(sampleUsage(mapper),
                new ThreadConfig("gpt-6-astra", "openai", "medium", "on-request",
                        "workspace-write", "/mnt/c/dev/VisitSeoul"),
                List.of(stylesheet("/css/base.css"), stylesheet("/css/dark.css")));
        VBox holder = new VBox(popup.root());
        holder.getStyleClass().add("root");
        holder.setStyle("-fx-background-color: -c-bg; -fx-padding: 24;");
        return holder;
    }

    private static UsageSnapshot sampleUsage(ObjectMapper mapper) {
        JsonNode tokenUsage = json(mapper, """
                {"total":{"totalTokens":53408,"inputTokens":51240,"cachedInputTokens":38200,
                          "outputTokens":2168,"reasoningOutputTokens":640},
                 "last":{"totalTokens":17859},"modelContextWindow":258400}""");
        JsonNode rateLimits = json(mapper, """
                {"planType":"plus",
                 "primary":{"usedPercent":86,"windowDurationMins":10080,"resetsAt":%d},
                 "secondary":{"usedPercent":41,"windowDurationMins":300,"resetsAt":%d},
                 "credits":{"hasCredits":false,"balance":"0"}}"""
                .formatted(System.currentTimeMillis() / 1000 + 320_000,
                        System.currentTimeMillis() / 1000 + 5_400));
        return UsageSnapshot.empty().withTokens(tokenUsage).withRateLimits(rateLimits);
    }

    private static JsonNode json(ObjectMapper mapper, String text) {
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("bad sample JSON: " + text, e);
        }
    }

    private static String stylesheet(String resource) {
        return UiPreviewTest.class.getResource(resource).toExternalForm();
    }

    /** Copies pixels into a BufferedImage so the JavaFX/Swing bridge module is not needed. */
    private static void writePng(WritableImage image, Path target) throws java.io.IOException {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        var reader = image.getPixelReader();
        var buffered = new java.awt.image.BufferedImage(width, height,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                row[x] = reader.getArgb(x, y);
            }
            buffered.setRGB(0, y, width, 1, row, 0, width);
        }
        javax.imageio.ImageIO.write(buffered, "png", target.toFile());
    }
}
