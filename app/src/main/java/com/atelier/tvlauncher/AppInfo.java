package com.atelier.tvlauncher;

import android.content.Intent;
import android.graphics.drawable.Drawable;

/** One launchable row entry (a streaming app or any leanback app). */
public class AppInfo {
    public String packageName;
    public String label;
    public Drawable icon;
    public Intent launchIntent;
    public boolean isStreaming;

    @Override
    public String toString() {
        return label;
    }
}
