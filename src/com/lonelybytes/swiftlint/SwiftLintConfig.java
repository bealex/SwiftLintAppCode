package com.lonelybytes.swiftlint;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.antlr.v4.runtime.misc.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.Collectors;

public class SwiftLintConfig {
    public static final String FILE_NAME = ".swiftlint.yml";

    static class Config {
        File _file;
        long _lastUpdateTime = 0;

        List<String> _excludedDirectories = new ArrayList<>();
        List<String> _includedDirectories = new ArrayList<>();

        Config(String aPath) {
            _file = new File(aPath);
            updateIfNeeded();
        }

        void updateIfNeeded() {
            if (_file.lastModified() > _lastUpdateTime) {
                try {
                    loadDisabledDirectories();
                } catch (Throwable aE) {
                    _excludedDirectories = Collections.emptyList();
                    _includedDirectories = Collections.emptyList();
                }

                _lastUpdateTime = _file.lastModified();
            }
        }

        @SuppressWarnings("unchecked")
        private void loadDisabledDirectories() throws FileNotFoundException {
            Yaml yaml = new Yaml();

            Map<String, Object> yamlData = yaml.load(new BufferedInputStream(new FileInputStream(_file)));
            _excludedDirectories = ((List<String>) yamlData.get("excluded"));
            _includedDirectories = ((List<String>) yamlData.get("included"));
        }
    }

    private final Map<String, Config> _configs = new HashMap<>();

    public SwiftLintConfig(Project aProject, String aConfigPath) {
        String _projectPath = aProject.getBasePath();

        String path = aConfigPath;
        if (path == null || !new File(path).exists()) {
            path = swiftLintConfigPath(aProject, 6);
        }
        if (path == null) {
            return;
        }

        _configs.put(_projectPath, new Config(path));
    }

    Config getConfig(String aFilePath) {
        File directory = new File(aFilePath);
        if (!directory.isDirectory()) {
            directory = directory.getParentFile();
        }

        Config config = _configs.get(directory.getAbsolutePath());
        while (config == null && directory.getParentFile() != null) {
            File possibleConfigPath = new File(directory.getAbsolutePath() + "/" + FILE_NAME);
            if (possibleConfigPath.exists()) {
                config = new Config(possibleConfigPath.getAbsolutePath());
                _configs.put(directory.getAbsolutePath(), config);
                break;
            }

            directory = directory.getParentFile();
            config = _configs.get(directory.getAbsolutePath());
        }

        if (config != null) {
            config.updateIfNeeded();
        }

        return config;
    }

    boolean shouldBeLinted(String aFilePath, boolean isLintedByDefault) {
        Config config = getConfig(aFilePath);
        if (config == null) {
            return isLintedByDefault;
        }

        boolean result = true;
        if (config._includedDirectories != null && !config._includedDirectories.isEmpty()) {
            result = config._includedDirectories.stream().anyMatch(aS -> aFilePath.contains("/" + aS + "/"));
        }

        if (config._excludedDirectories != null && !config._excludedDirectories.isEmpty()) {
            result = result && config._excludedDirectories.stream().noneMatch(aS -> aFilePath.contains("/" + aS + "/"));
        }

        return result;
    }

    private static class DepthedFile {
        int _depth;
        VirtualFile _file;

        DepthedFile(int aDepth, VirtualFile aFile) {
            _depth = aDepth;
            _file = aFile;
        }
    }

    @Nullable
    public static String swiftLintConfigPath(Project aProject, int aDepthToLookAt) {
        ProjectRootManager projectRootManager = ProjectRootManager.getInstance(aProject);
        VirtualFile[] roots = projectRootManager.getContentRoots();
        for (VirtualFile root : roots) {
            VirtualFile configFile = root.findChild(FILE_NAME);
            if (configFile != null) {
                return configFile.getCanonicalPath();
            }
        }

        List<DepthedFile> filesToLookAt = Arrays.stream(roots)
                .flatMap(aVirtualFile -> Arrays.stream(aVirtualFile.getChildren()))
                .filter(VirtualFile::isDirectory)
                .map(aVirtualFile -> new DepthedFile(0, aVirtualFile))
                .collect(Collectors.toCollection(LinkedList::new));

        while (!filesToLookAt.isEmpty()) {
            DepthedFile file = filesToLookAt.get(0);
            filesToLookAt.remove(0);

            if (file._depth > aDepthToLookAt) {
                break;
            }

            if (file._file.findChild(FILE_NAME) != null) {
                return file._file + "/" + FILE_NAME;
            } else {
                filesToLookAt.addAll(
                        Arrays.stream(file._file.getChildren())
                                .filter(VirtualFile::isDirectory)
                                .map(aVirtualFile -> new DepthedFile(file._depth + 1, aVirtualFile))
                                .collect(Collectors.toList())
                );
            }
        }

        return null;
    }
}
