package ch.uzh.ifi.access.student.evaluation.runner;

import ch.uzh.ifi.access.course.model.VirtualFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;

@Component
public class FSHierarchySerializer {

    private static final Logger logger = LoggerFactory.getLogger(FSHierarchySerializer.class);

    private static final String INIT_FILE = "__init__.py";

    private static final String CHMOD_755 = "rwxr-xr-x";

    private List<String> executableExtensions = List.of(".sh", ".py");

    protected void persistFilesIntoFolder(String rootPath, List<VirtualFile> files) {
        if (files == null) {
            logger.debug("No files to persist @ " + rootPath);
            return;
        }

        Path path = Paths.get(rootPath);
        logger.debug("Writing folder @ {}", path.toAbsolutePath().normalize().toString());

        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);

                for (VirtualFile vf : files) {

                    File parent = Paths.get(rootPath, vf.getPath()).toFile().getParentFile();
                    boolean createdParentsFolders = parent.mkdirs();
                    logger.debug("Created parent folders: {}", createdParentsFolders);

                    Path file = Files.createFile(Paths.get(rootPath, vf.getPath()));
                    file = Files.writeString(file, vf.getContent());
                    if (isExecutableFile(file)) {
                        logger.debug("Setting 755 on file {}", file.getFileName());
                        setExecutePermissions(file);
                    }

                    Path initInFolder = Paths.get(parent.getPath(), INIT_FILE);
                    if (!Files.exists(initInFolder)) {
                        Files.createFile(initInFolder);
                        logger.debug("Created missing __init__.py file @ {}", initInFolder.toString());
                    }
                }

                if (!Files.exists(Paths.get(rootPath, INIT_FILE))) {
                    Files.createFile(Paths.get(rootPath, INIT_FILE));
                }

            } catch (IOException e) {
                logger.error("Failed to serialize files to file system", e);
            }
        } else {
            logger.warn("Root folder for execution already exists, aborting.");
        }
    }

    private boolean isExecutableFile(Path file) {
        Path fileName = file.getFileName();
        return fileName != null && executableExtensions
                .stream()
                .anyMatch(extension -> fileName.toString().endsWith(extension));
    }

    private void setExecutePermissions(Path file) throws IOException {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(CHMOD_755));
    }

    protected void removeDirectory(Path path) throws IOException {
        logger.debug("Removing temp directory @ {}", path);
        Files
                .walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(this::removeFile);
    }

    private void removeFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.error("Failed to remove file @ {}", path, e);
        }
    }
}
