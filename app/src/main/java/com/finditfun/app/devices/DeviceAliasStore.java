package com.finditfun.app.devices;

import android.content.Context;
import android.content.SharedPreferences;

public final class DeviceAliasStore {
    private static final String PREFERENCES = "my_devices";
    private static final String ALIAS_PREFIX = "alias:";

    private final SharedPreferences preferences;

    public DeviceAliasStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public String aliasFor(String deviceKey) {
        return preferences.getString(ALIAS_PREFIX + deviceKey, null);
    }

    public boolean isSaved(String deviceKey) {
        String alias = aliasFor(deviceKey);
        return alias != null && !alias.trim().isEmpty();
    }

    public void save(String deviceKey, String alias) {
        String cleaned = alias == null ? "" : alias.trim();
        if (cleaned.length() > 60) cleaned = cleaned.substring(0, 60).trim();
        if (cleaned.isEmpty()) {
            forget(deviceKey);
        } else {
            preferences.edit().putString(ALIAS_PREFIX + deviceKey, cleaned).apply();
        }
    }

    public void forget(String deviceKey) {
        preferences.edit().remove(ALIAS_PREFIX + deviceKey).apply();
    }
}
