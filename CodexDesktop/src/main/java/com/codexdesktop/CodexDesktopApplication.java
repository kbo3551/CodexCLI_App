package com.codexdesktop;

import com.codexdesktop.codex.CodexAppServerClient;
import com.codexdesktop.codex.CodexHost;
import com.codexdesktop.codex.CodexSessionService;
import com.codexdesktop.codex.WslCodexHost;
import com.codexdesktop.i18n.I18n;
import com.codexdesktop.model.AppSettings;
import com.codexdesktop.model.ProjectInfo;
import com.codexdesktop.service.AttachmentService;
import com.codexdesktop.service.DiagnosticsService;
import com.codexdesktop.service.GitService;
import com.codexdesktop.service.ProjectService;
import com.codexdesktop.service.SettingsService;
import com.codexdesktop.service.WindowsWslPathConverter;
import com.codexdesktop.service.WslCommandRunner;
import com.codexdesktop.ui.FontLoader;
import com.codexdesktop.ui.MainWindow;
import com.codexdesktop.ui.SettingsView;
import com.codexdesktop.ui.ThemeManager;
import com.codexdesktop.ui.markdown.MarkdownRenderer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

/**
 * JavaFX entry point.
 *
 * <p>Wiring is explicit rather than framework-driven: this is a desktop client with a handful of
 * services, so a constructor graph is clearer and starts faster than a container.
 */
public final class CodexDesktopApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(CodexDesktopApplication.class);
    private static final List<String> ICON_RESOURCES =
            List.of("/icons/app-16.png", "/icons/app-32.png", "/icons/app-64.png",
                    "/icons/app-128.png", "/icons/app-256.png");

    private CodexSessionService session;
    private SettingsService settingsService;
    private ProjectService projectService;
    private GitService gitService;
    private AttachmentService attachmentService;
    private DiagnosticsService diagnosticsService;
    private MarkdownRenderer renderer;
    private ThemeManager themeManager;
    private Scene scene;
    private MainWindow mainWindow;

    @Override
    public void start(Stage stage) {
        log.info("CodexDesktop starting (Java {}, JavaFX {})",
                System.getProperty("java.version"), System.getProperty("javafx.runtime.version"));

        // Register the bundled font before any node is styled, so nothing renders with a fallback.
        FontLoader.loadBundledFonts();

        ObjectMapper mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        settingsService = new SettingsService(mapper);
        AppSettings settings = settingsService.get();
        I18n.setLanguage(settings.language());

        WslCommandRunner runner = new WslCommandRunner(settings.wslExecutable(), settings.distribution());
        WindowsWslPathConverter pathConverter = new WindowsWslPathConverter(runner);
        projectService = new ProjectService(mapper, pathConverter);
        gitService = new GitService(runner);
        attachmentService = new AttachmentService(pathConverter);
        attachmentService.pruneOldAttachments();
        diagnosticsService = new DiagnosticsService();
        renderer = new MarkdownRenderer(this::openLink);

        CodexHost host = new WslCodexHost();
        CodexAppServerClient client = new CodexAppServerClient(host, mapper);
        client.setDebugWireLogging(settings.debugLogging());
        // The raw-protocol logger is OFF in logback.xml; a saved preference has to raise it here or
        // "Debug Logging" would only take effect after re-saving the setting.
        applyDebugLogLevel(settings.debugLogging());
        client.addStderrListener(line -> log.debug("codex stderr: {}", line));
        session = new CodexSessionService(client);

        mainWindow = buildShell();
        scene = new Scene(mainWindow, 1280, 840);
        themeManager = new ThemeManager(scene);
        applyAppearance(settings);
        registerShortcuts();

        stage.setScene(scene);
        stage.setTitle(I18n.t("app.title"));
        stage.setMinWidth(900);
        stage.setMinHeight(580);
        loadIcons(stage);
        stage.setOnCloseRequest(event -> shutdown());
        stage.show();

        mainWindow.restoreLastSession();
        mainWindow.focusComposer();
    }

    private MainWindow buildShell() {
        SettingsView settingsView = new SettingsView(diagnosticsService);
        MainWindow window = new MainWindow(session, projectService, settingsService, gitService,
                attachmentService, settingsView, renderer);
        window.setSettingsApplier(this::onSettingsChanged);
        return window;
    }

    /**
     * Applies appearance settings. A language change needs a fresh widget tree because labels are
     * created with their text, so the shell is rebuilt and the active project reopened.
     */
    private void onSettingsChanged(AppSettings updated) {
        boolean languageChanged = !updated.language().equals(I18n.currentSetting());
        applyAppearance(updated);
        if (!languageChanged) {
            return;
        }
        I18n.setLanguage(updated.language());
        ProjectInfo previous = mainWindow.activeProject();
        mainWindow = buildShell();
        scene.setRoot(mainWindow);
        applyAppearance(updated);
        registerShortcuts();
        if (previous != null) {
            mainWindow.openProject(previous);
        }
        mainWindow.focusComposer();
    }

    private void applyAppearance(AppSettings settings) {
        themeManager.apply(settings.theme());
        // Root inline style drives the whole UI: sizes in the CSS are relative to this, and the
        // family cascades to every node except the ones that pin a monospace face themselves.
        StringBuilder style = new StringBuilder("-fx-font-size: ")
                .append(settings.rootFontSize()).append("px;");
        if (settings.hasCustomFont()) {
            style.append(" -fx-font-family: '").append(settings.uiFont()).append("';");
        }
        scene.getRoot().setStyle(style.toString());
    }

    private void registerShortcuts() {
        scene.getAccelerators().clear();
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                () -> mainWindow.focusComposer());
    }

    private static void loadIcons(Stage stage) {
        for (String resource : ICON_RESOURCES) {
            try (InputStream stream = CodexDesktopApplication.class.getResourceAsStream(resource)) {
                if (stream != null) {
                    stage.getIcons().add(new Image(stream));
                }
            } catch (Exception e) {
                log.debug("Could not load icon {}: {}", resource, e.getMessage());
            }
        }
    }

    /** Raises or silences the raw-protocol logger. */
    public static void applyDebugLogLevel(boolean enabled) {
        if (LoggerFactory.getLogger("codex.wire") instanceof ch.qos.logback.classic.Logger wire) {
            wire.setLevel(enabled ? ch.qos.logback.classic.Level.DEBUG
                    : ch.qos.logback.classic.Level.OFF);
        }
    }

    private void openLink(String url) {
        try {
            getHostServices().showDocument(url);
        } catch (RuntimeException e) {
            log.warn("Could not open link {}: {}", url, e.getMessage());
        }
    }

    @Override
    public void stop() {
        shutdown();
    }

    /**
     * Stops only the app-server this process started. Codex sessions the user launched elsewhere
     * are untouched — no process sweeping by name.
     */
    private void shutdown() {
        if (mainWindow != null) {
            mainWindow.closeAuxiliaryWindows();
        }
        if (session != null) {
            log.info("Shutting down Codex session");
            session.shutdown();
            session = null;
        }
        log.info("CodexDesktop stopped");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
