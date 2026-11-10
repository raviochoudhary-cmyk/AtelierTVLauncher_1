package com.atelier.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Persists the user's custom streaming-row ordering (package names, one per line). */
public class LauncherPreferences {

    private static final String PREFS = "atelier_prefs";
    private static final String KEY_STREAMING_ORDER = "streaming_order_v1";

    private final SharedPreferences sp;

    public LauncherPreferences(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<String> getStreamingOrder() {
        List<String> out = new ArrayList<>();
        String raw = sp.getString(KEY_STREAMING_ORDER, "");
        if (raw != null && !raw.isEmpty()) {
            for (String p : raw.split("\n")) {
                if (!p.trim().isEmpty()) out.add(p.trim());
            }
        }
        return out;
    }

    public void saveStreamingOrder(List<String> order) {
        StringBuilder sb = new StringBuilder();
        for (String p : order) {
            sb.append(p).append('\n');
        }
        sp.edit().putString(KEY_STREAMING_ORDER, sb.toString()).apply();
    }
}
