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

class SwiftLintConfig {
    private Map<String, Boolean> enabledRules = new HashMap<>();
    private Map<String, Boolean> disabledRules = new HashMap<>();

    private String _configPath = null;

    static final String[][] rules = {
            {"attributes", "Attributes should be on their own lines in functions and types, but on the same line as variables and imports."},
            {"closing_brace", "Closing brace with closing parenthesis should not have any whitespaces in the middle."},
            {"closure_end_indentation", ""},
            {"closure_parameter_position", ""},
            {"closure_spacing", ""},
            {"colon", ""},
            {"comma", ""},
            {"conditional_returns_on_newline", ""},
            {"control_statement", ""},
            {"custom_rules", ""},
            {"cyclomatic_complexity", ""},
            {"dynamic_inline", ""},
            {"empty_count", ""},
            {"empty_parameters", ""},
            {"empty_parentheses_with_trailing_closure", ""},
            {"explicit_init", ""},
            {"file_header", ""},
            {"file_length", ""},
            {"first_where", ""},
            {"force_cast", ""},
            {"force_try", ""},
            {"force_unwrapping", ""},
            {"function_body_length", ""},
            {"function_parameter_count", ""},
            {"implicit_getter", ""},
            {"leading_whitespace", ""},
            {"legacy_cggeometry_functions", ""},
            {"legacy_constant", ""},
            {"legacy_constructor", ""},
            {"legacy_nsgeometry_functions", ""},
            {"line_length", ""},
            {"mark", ""},
            {"missing_docs", ""},
            {"nesting", ""},
            {"nimble_operator", ""},
            {"number_separator", ""},
            {"opening_brace", ""},
            {"operator_usage_whitespace", ""},
            {"operator_whitespace", ""},
            {"overridden_super_call", ""},
            {"private_outlet", ""},
            {"private_unit_test", ""},
            {"prohibited_super_call", ""},
            {"redundant_nil_coalescing", ""},
            {"redundant_string_enum_value", ""},
            {"return_arrow_whitespace", ""},
            {"statement_position", ""},
            {"switch_case_on_newline", ""},
            {"syntactic_sugar", ""},
            {"todo", ""},
            {"trailing_comma", ""},
            {"trailing_newline", ""},
            {"trailing_semicolon", ""},
            {"trailing_whitespace", ""},
            {"type_body_length", ""},
            {"type_name", ""},
            {"unused_closure_parameter", ""},
            {"unused_enumerated", ""},
            {"valid_docs", ""},
            {"valid_ibinspectable", ""},
            {"variable_name", ""},
            {"vertical_whitespace", ""},
            {"void_return", ""},
            {"weak_delegate", ""},
    };

    private static List<String> ruleNames = new ArrayList<String>() {{
        for (String[] rule : rules) {
            add(rule[0]);
        }
    }};

    private static Map<String, String> ruleToDescription = new HashMap<String, String>() {{
        for (String[] rule : rules) {
            put(rule[0], rule[1]);
        }
    }};

    static enum Severity {
        Disabled, Warning, Error
    }

    // true for enabled rule, false for disabled
    private Map<String, Severity> rulesSeverity = new HashMap<>();

    @SuppressWarnings("unchecked")
    SwiftLintConfig(Project aProject) {
        String swiftLintConfigPath = swiftLintConfigPath(aProject, 6);
        if (swiftLintConfigPath != null) {
            try {
                Yaml yaml = new Yaml();
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) yaml.load(new BufferedInputStream(new FileInputStream(new File(swiftLintConfigPath))));

                List<String> disabledRules = (List<String>) config.get("disabled_rules");
                processDisabledRules(disabledRules);
                List<String> optInRules = (List<String>) config.get("opt_in_rules");
                processOptInRules(optInRules);

                for (Map.Entry<String, Object> entry : config.entrySet()) {
                    if (entry.getValue() instanceof String) {
                        String value = (String) entry.getValue();

                        if (value.equals("error")) {
                            setRuleSeverity(entry.getKey(), Severity.Error);
                        } else if (value.equals("warning")) {
                            setRuleSeverity(entry.getKey(), Severity.Warning);
                        }
                    } else if (entry.getValue() instanceof Map) {
                        Map<String, Object> values = (Map<String, Object>) entry.getValue();
                        for (Map.Entry<String, Object> valueEntry : values.entrySet()) {
                            String ruleName = valueEntry.getKey();

                            if (ruleName.equals("severity")) {
                                if (valueEntry.getValue().equals("error")) {
                                    setRuleSeverity(ruleName, Severity.Error);
                                } else if (valueEntry.getValue().equals("warning")) {
                                    setRuleSeverity(ruleName, Severity.Warning);
                                } else if (valueEntry.getValue().equals("disabled")) {
                                    setRuleSeverity(ruleName, Severity.Disabled);
                                }
                            }
                        }
                    }
                }
            } catch (FileNotFoundException aE) {
                aE.printStackTrace();
            }
        }
    }

    Severity ruleSeverity(String aRuleName) {
        Severity result = rulesSeverity.get(aRuleName);
        return result == null ? Severity.Disabled : result;
    }

    private void processOptInRules(List<String> aOptInRules) {
        for (String rule : aOptInRules) {
            setRuleSeverity(rule, Severity.Warning);
        }
    }

    private void processDisabledRules(List<String> aDisabledRules) {
        for (String rule : aDisabledRules) {
            setRuleSeverity(rule, Severity.Disabled);
        }
    }

    private void setRuleSeverity(String aRuleName, Severity aSeverity) {
        if (ruleNames.contains(aRuleName)) {
            rulesSeverity.put(aRuleName, aSeverity);
        } else {
            System.err.println("Unknown rule: " + aRuleName);
        }
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
    static String swiftLintConfigPath(Project aProject, int aDepthToLookAt) {
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
