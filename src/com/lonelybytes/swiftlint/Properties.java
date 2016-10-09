package com.lonelybytes.swiftlint;

import com.intellij.ide.util.PropertiesComponent;


public class Properties {
    private static final PropertiesComponent INSTANCE = PropertiesComponent.getInstance();

    static void set(String key, String value) {
        INSTANCE.setValue(key, value);
    }

    static String get(String key) {
        return INSTANCE.getValue(key);
    }

    public static boolean isEmpty(String key) {
        String prop = get(key);
        return prop == null || prop.isEmpty();
    }
}
