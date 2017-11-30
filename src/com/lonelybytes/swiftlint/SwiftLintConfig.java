package com.lonelybytes.swiftlint;

import com.intellij.openapi.project.Project;
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
    private String _projectPath = null;
    private String _configPath = null;
    private long _configPathLastUpdateTime = 0;

    private List<String> _disabledDirectories = new ArrayList<>();
    
    @SuppressWarnings("unchecked")
    public SwiftLintConfig(Project aProject, String aConfigPath) {
        update(aProject, aConfigPath);
    }

    public String getConfigPath() {
        return _configPath;
    }

    boolean isDisabled(String aFilePath) {
        return _disabledDirectories.stream().anyMatch(aS -> aFilePath.contains("/" + aS + "/"));
    }

    public void update(Project aProject, String aConfigPath) {
        if (_projectPath != null && Objects.equals(_projectPath, aProject.getBasePath()) && Objects.equals(_configPath, aConfigPath)) {
            File configFile = new File(_configPath);
            if (_configPath != null && configFile.exists()) {
                if (configFile.lastModified() <= _configPathLastUpdateTime) {
                    return;
                }

                _configPathLastUpdateTime = configFile.lastModified();
            } else {
                return;
            }
        }

        _projectPath = aProject.getBasePath();
        _configPath = aConfigPath;

        if (_configPath == null) {
            _configPath = swiftLintConfigPath(aProject, 6);
        }

        try {
            loadDisabledDirectories();
        } catch (FileNotFoundException aE) {
            aE.printStackTrace();
        }
    }

    private void loadDisabledDirectories() throws FileNotFoundException {
        Yaml yaml = new Yaml();

        //noinspection unchecked
        Map<String, Object> yamlData = (Map<String, Object>) yaml.load(new BufferedInputStream(new FileInputStream(new File(_configPath))));
        //noinspection unchecked
        _disabledDirectories = ((List<String>) yamlData.get("excluded"));
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
        if (aProject.getBaseDir().findChild(".swiftlint.yml") != null) {
            return aProject.getBaseDir().getCanonicalPath() + "/.swiftlint.yml";
        }

        List<DepthedFile> filesToLookAt = new LinkedList<>();
        filesToLookAt.addAll(
                Arrays.stream(aProject.getBaseDir().getChildren())
                        .filter(VirtualFile::isDirectory)
                        .map(aVirtualFile -> new DepthedFile(0, aVirtualFile))
                        .collect(Collectors.toList())
        );

        while (!filesToLookAt.isEmpty()) {
            DepthedFile file = filesToLookAt.get(0);
            filesToLookAt.remove(0);

            if (file._depth > aDepthToLookAt) {
                break;
            }

            if (file._file.findChild(".swiftlint.yml") != null) {
                return file._file + "/.swiftlint.yml";
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
