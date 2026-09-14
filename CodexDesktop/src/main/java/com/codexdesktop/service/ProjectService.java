package com.codexdesktop.service;

import com.codexdesktop.model.ProjectInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the list of known projects in an observable list backed by a JSON file.
 *
 * <p>Deliberately file-based: the data is a handful of paths, so a database would add
 * dependencies and startup cost for no benefit.
 */
public final class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);
    private static final String FILE_NAME = "projects.json";

    private final ObjectMapper mapper;
    private final Path file;
    private final ObservableList<ProjectInfo> projects = FXCollections.observableArrayList();
    private final WindowsWslPathConverter pathConverter;

    public ProjectService(ObjectMapper mapper, WindowsWslPathConverter pathConverter) {
        this(mapper, pathConverter, SettingsService.appDataDirectory().resolve(FILE_NAME));
    }

    ProjectService(ObjectMapper mapper, WindowsWslPathConverter pathConverter, Path file) {
        this.mapper = mapper;
        this.pathConverter = pathConverter;
        this.file = file;
        projects.setAll(load());
    }

    public ObservableList<ProjectInfo> projects() {
        return projects;
    }

    /**
     * Registers a Windows directory as a project, converting it to its WSL path.
     *
     * @return the existing entry when the directory is already registered
     */
    public ProjectInfo addProject(Path windowsDirectory) {
        String windowsPath = windowsDirectory.toAbsolutePath().toString();
        Optional<ProjectInfo> existing = projects.stream()
                .filter(p -> p.windowsPath().equalsIgnoreCase(windowsPath))
                .findFirst();
        if (existing.isPresent()) {
            ProjectInfo touched = existing.get().touched();
            replace(touched);
            return touched;
        }
        String wslPath = pathConverter.toWslPath(windowsPath);
        String name = windowsDirectory.getFileName() == null
                ? windowsPath
                : windowsDirectory.getFileName().toString();
        ProjectInfo project = new ProjectInfo(UUID.randomUUID().toString(), name, windowsPath, wslPath,
                null, System.currentTimeMillis());
        projects.add(project);
        save();
        log.info("Added project {} -> {}", windowsPath, wslPath);
        return project;
    }

    public void removeProject(ProjectInfo project) {
        projects.removeIf(p -> p.id().equals(project.id()));
        save();
    }

    /** Replaces an entry by id, keeping list order stable. */
    public void replace(ProjectInfo project) {
        for (int i = 0; i < projects.size(); i++) {
            if (projects.get(i).id().equals(project.id())) {
                projects.set(i, project);
                save();
                return;
            }
        }
    }

    public void rememberLastThread(ProjectInfo project, String threadId) {
        replace(project.withLastThread(threadId));
    }

    public Optional<ProjectInfo> mostRecent() {
        return projects.stream().max(Comparator.comparingLong(ProjectInfo::lastOpenedAt));
    }

    public Optional<ProjectInfo> byId(String id) {
        return projects.stream().filter(p -> p.id().equals(id)).findFirst();
    }

    private List<ProjectInfo> load() {
        try {
            if (Files.exists(file)) {
                CollectionType type = mapper.getTypeFactory()
                        .constructCollectionType(ArrayList.class, ProjectInfo.class);
                List<ProjectInfo> loaded = mapper.readValue(Files.readString(file), type);
                if (loaded != null) {
                    return loaded;
                }
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read {} ({}), starting with no projects", file, e.getMessage());
        }
        return List.of();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new ArrayList<>(projects)));
        } catch (IOException | RuntimeException e) {
            log.warn("Could not write projects to {}: {}", file, e.getMessage());
        }
    }
}
