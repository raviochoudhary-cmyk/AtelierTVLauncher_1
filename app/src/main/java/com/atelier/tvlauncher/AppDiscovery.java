package com.atelier.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Discovers installed Android TV applications via PackageManager.
 * Nothing is hardcoded without an existence check: every package below is
 * only shown if PackageManager reports it installed with a leanback intent.
 */
public final class AppDiscovery {

    private static final String TAG = "AtelierApps";

    /** Preferred order for well-known streaming apps. Existence is checked. */
    private static final List<String> STREAMING_PRIORITY = Arrays.asList(
            "com.netflix.ninja",                    // Netflix
            "com.amazon.amazonvideo.livingroom",    // Prime Video (Fire TV build)
            "com.amazon.amazonvideo.thirdpartyclient", // Prime Video (Google TV build)
            "com.google.android.youtube.tv",        // YouTube for Android TV
            "com.google.android.youtube",           // YouTube (sideloaded)
            "in.startv.hotstar",                    // JioHotstar / Disney+ Hotstar
            "com.disney.disneyplus",                // Disney+
            "com.apple.atv.android.tv",             // Apple TV (Google TV build)
            "com.apple.atv.android",                // Apple TV (sideloaded)
            "in.jio.jioplay.tv",                    // JioTV
            "com.sonyliv",                          // SonyLIV
            "com.graymatrix.did",                   // ZEE5 (Android TV)
            "com.viacom18.voottv",                  // Voot / JioCinema
            "tv.accedo.airtel.wynk"                 // Airtel Xstream / Wynk
    );

    /** Label keywords used to detect additional streaming apps. */
    private static final List<String> STREAMING_KEYWORDS = Arrays.asList(
            "netflix", "prime video", "youtube", "hotstar", "disney", "apple tv",
            "jiotv", "jio", "sonyliv", "sony liv", "zee5", "voot", "xstream",
            "wynk", "hbo", "hulu", "plex", "mx player", "eros", "altbalaji",
            "shemaroo", "tata play", "sun nxt", "aha", "zee"
    );

    private AppDiscovery() {
    }

    /** Every installed app that exposes a leanback launcher intent. */
    public static List<AppInfo> discoverLeanbackApps(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN);
        probe.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);

        List<ResolveInfo> infos;
        try {
            infos = pm.queryIntentActivities(probe, PackageManager.GET_META_DATA);
        } catch (Throwable t) {
            Log.w(TAG, "queryIntentActivities failed", t);
            return new ArrayList<>();
        }

        List<AppInfo> out = new ArrayList<>();
        for (ResolveInfo ri : infos) {
            if (ri == null || ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg == null || pkg.equals(ctx.getPackageName())) continue;

            AppInfo app = new AppInfo();
            app.packageName = pkg;
            CharSequence label = ri.loadLabel(pm);
            app.label = (label != null && label.length() > 0) ? label.toString() : pkg;
            try {
                app.icon = ri.loadIcon(pm);
            } catch (Throwable t) {
                app.icon = pm.getDefaultActivityIcon();
            }
            app.launchIntent = new Intent(Intent.ACTION_MAIN);
            app.launchIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            app.launchIntent.setClassName(pkg, ri.activityInfo.name);
            app.launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.isStreaming = isStreaming(pkg, app.label);
            out.add(app);
        }

        Collections.sort(out, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        return out;
    }

    /**
     * The streaming row: user-saved custom order first, then well-known
     * streaming apps in priority order, then other streaming apps, then the
     * remaining leanback apps.
     */
    public static List<AppInfo> getStreamingRowApps(Context ctx) {
        List<AppInfo> all = discoverLeanbackApps(ctx);
        List<AppInfo> streaming = new ArrayList<>();
        List<AppInfo> others = new ArrayList<>();
        for (AppInfo a : all) {
            (a.isStreaming ? streaming : others).add(a);
        }

        List<String> savedOrder = new LauncherPreferences(ctx).getStreamingOrder();
        Set<String> consumed = new HashSet<>();
        List<AppInfo> ordered = new ArrayList<>();

        for (String pkg : savedOrder) {
            AppInfo a = findByPackage(streaming, pkg);
            if (a != null) {
                ordered.add(a);
                consumed.add(pkg);
            }
        }
        for (String pkg : STREAMING_PRIORITY) {
            if (consumed.contains(pkg)) continue;
            AppInfo a = findByPackage(streaming, pkg);
            if (a != null) {
                ordered.add(a);
                consumed.add(pkg);
            }
        }
        for (AppInfo a : streaming) {
            if (!consumed.contains(a.packageName)) ordered.add(a);
        }
        ordered.addAll(others);
        return ordered;
    }

    private static AppInfo findByPackage(List<AppInfo> apps, String pkg) {
        for (AppInfo a : apps) {
            if (a.packageName.equals(pkg)) return a;
        }
        return null;
    }

    private static boolean isStreaming(String pkg, String label) {
        if (STREAMING_PRIORITY.contains(pkg)) return true;
        String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
        for (String kw : STREAMING_KEYWORDS) {
            if (l.contains(kw)) return true;
        }
        return false;
    }
}
