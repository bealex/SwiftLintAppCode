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

    static void set(String key, boolean value) {
        INSTANCE.setValue(key, value);
    }

    static boolean getBoolean(String key) {
        return INSTANCE.getBoolean(key);
    }

    public static boolean isEmpty(String key) {
        String prop = get(key);
        return prop == null || prop.isEmpty();
    }
}
