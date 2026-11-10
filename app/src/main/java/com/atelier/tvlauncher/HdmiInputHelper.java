package com.atelier.tvlauncher;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Discovers TV inputs (HDMI, Live TV / tuner, AV) through TvInputManager.
 * Every call is defensive: if the OEM input stack is not exposed through the
 * standard API, we return an empty list and the UI falls back to a documented
 * input-selection dialog instead of crashing.
 */
public final class HdmiInputHelper {

    private static final String TAG = "AtelierHdmi";

    public static class Entry {
        public TvInputInfo info;
        public String id;
        public String label;
        public int type;
        public boolean isTuner;

        @Override
        public String toString() {
            return label;
        }
    }

    private HdmiInputHelper() {
    }

    /** All user-visible hardware inputs reported by the platform. */
    public static List<Entry> getInputs(Context ctx) {
        List<Entry> out = new ArrayList<>();
        final TvInputManager tm;
        try {
            tm = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        } catch (Throwable t) {
            Log.w(TAG, "TvInputManager unavailable", t);
            return out;
        }
        if (tm == null) return out;

        List<TvInputInfo> infos;
        try {
            infos = tm.getTvInputList();
        } catch (Throwable t) {
            Log.w(TAG, "getTvInputList failed", t);
            return out;
        }
        if (infos == null) return out;

        for (TvInputInfo info : infos) {
            try {
                int type = info.getType();
                boolean supported = type == TvInputInfo.TYPE_HDMI
                        || type == TvInputInfo.TYPE_TUNER
                        || type == TvInputInfo.TYPE_COMPOSITE
                        || type == TvInputInfo.TYPE_SVIDEO
                        || type == TvInputInfo.TYPE_COMPONENT
                        || type == TvInputInfo.TYPE_SCART;
                if (!supported) continue;

                Entry e = new Entry();
                e.info = info;
                e.id = info.getId();
                e.type = type;
                e.isTuner = (type == TvInputInfo.TYPE_TUNER);

                CharSequence label = null;
                try {
                    label = info.loadCustomLabel(ctx);
                } catch (Throwable ignored) {
                }
                if (label == null || label.length() == 0) {
                    try {
                        label = info.loadLabel(ctx);
                    } catch (Throwable ignored) {
                    }
                }
                e.label = (label != null && label.length() > 0) ? label.toString() : info.getId();
                out.add(e);
            } catch (Throwable t) {
                Log.w(TAG, "Skipping unreadable TvInputInfo", t);
            }
        }
        return out;
    }

    /** Direct switch: ACTION_VIEW on the passthrough channel URI for this input. */
    public static boolean launchPassthrough(Context ctx, Entry e) {
        try {
            Uri uri = TvContract.buildChannelUriForPassthroughInput(e.id);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Passthrough launch failed for " + e.id, t);
            return false;
        }
    }

    /** Fallback: the input's own setup activity, when it exposes one. */
    public static boolean launchSetup(Context ctx, Entry e) {
        try {
            Intent intent = e.info.createSetupIntent();
            if (intent == null) return false;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Setup launch failed for " + e.id, t);
            return false;
        }
    }
}
