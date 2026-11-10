package com.atelier.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Atelier TV home screen. Framework-only (no androidx): a vertical stack of
 * horizontal rows, fully D-pad navigable. Zones in top-to-bottom order:
 * header settings -> streaming row -> inputs row -> bottom shortcuts.
 */
public class MainActivity extends Activity {

    private static final String TAG = "AtelierMain";

    private LauncherPreferences prefs;
    private TextView clockView;
    private LinearLayout rowsContainer;

    /** Vertical focus chain. Each entry is either a single View or a LinearLayout row. */
    private final List<Object> focusZones = new ArrayList<>();
    private final List<String> displayedStreamingPackages = new ArrayList<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 20_000L);
        }
    };

    /** Subtle scale on focus; cheap and stops immediately when unfocused. */
    private final View.OnFocusChangeListener focusFx = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            float target = hasFocus ? 1.10f : 1.0f;
            v.animate().cancel();
            v.animate().scaleX(target).scaleY(target).setDuration(120L).start();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new LauncherPreferences(this);
        clockView = findViewById(R.id.clock);
        rowsContainer = findViewById(R.id.rows_container);

        wireShortcut(R.id.header_settings, new Intent(Settings.ACTION_SETTINGS));
        wireShortcut(R.id.btn_settings, new Intent(Settings.ACTION_SETTINGS));
        wireShortcut(R.id.btn_wifi, new Intent(Settings.ACTION_WIFI_SETTINGS));
        wireShortcut(R.id.btn_all_apps, new Intent(this, AllAppsActivity.class));
        wireShortcut(R.id.btn_refresh, null); // handled specially: rebuild rows

        rebuildRows();
    }

    private void wireShortcut(int id, final Intent intent) {
        View v = findViewById(id);
        v.setOnFocusChangeListener(focusFx);
        if (id == R.id.btn_refresh) {
            v.setOnClickListener(view -> {
                Toast.makeText(this, R.string.refreshing, Toast.LENGTH_SHORT).show();
                rebuildRows();
            });
        } else {
            final Intent safe = intent == null ? null : intent;
            v.setOnClickListener(view -> startActivitySafely(safe));
        }
    }

    // ------------------------------------------------------------------ rows

    private void rebuildRows() {
        displayedStreamingPackages.clear();
        focusZones.clear();
        rowsContainer.removeAllViews();

        focusZones.add(findViewById(R.id.header_settings));

        // Streaming row (primary).
        rowsContainer.addView(rowHeader(getString(R.string.row_streaming)));
        LinearLayout streamingRow = buildRow();
        List<AppInfo> streamingApps = AppDiscovery.getStreamingRowApps(this);
        for (AppInfo app : streamingApps) {
            streamingRow.addView(createAppCard(app));
            displayedStreamingPackages.add(app.packageName);
        }
        rowsContainer.addView(wrapInScroller(streamingRow));
        focusZones.add(streamingRow);

        // Inputs row (HDMI / Live TV / AV).
        rowsContainer.addView(rowHeader(getString(R.string.row_inputs)));
        LinearLayout inputsRow = buildRow();
        addInputCards(inputsRow);
        rowsContainer.addView(wrapInScroller(inputsRow));
        focusZones.add(inputsRow);

        // Bottom shortcuts, left to right = top to bottom in the focus chain.
        focusZones.add(findViewById(R.id.btn_settings));
        focusZones.add(findViewById(R.id.btn_wifi));
        focusZones.add(findViewById(R.id.btn_all_apps));
        focusZones.add(findViewById(R.id.btn_refresh));

        rowsContainer.post(() -> {
            if (streamingRow.getChildCount() > 0) {
                streamingRow.getChildAt(0).requestFocus();
            }
        });
    }

    private TextView rowHeader(String text) {
        TextView h = (TextView) getLayoutInflater().inflate(R.layout.row_header, null);
        h.setText(text);
        return h;
    }

    private LinearLayout buildRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        return row;
    }

    private HorizontalScrollView wrapInScroller(View content) {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.addView(content, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroller;
    }

    // ------------------------------------------------------------------ cards

    private View createAppCard(final AppInfo app) {
        View card = getLayoutInflater().inflate(R.layout.item_app_card, null);
        ImageView icon = card.findViewById(R.id.card_icon);
        TextView label = card.findViewById(R.id.card_label);
        icon.setImageDrawable(app.icon);
        label.setText(app.label);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.card_width),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = getResources().getDimensionPixelSize(R.dimen.card_margin);
        lp.setMargins(m, m, m, m);
        card.setLayoutParams(lp);

        card.setOnClickListener(v -> launchApp(app));
        card.setOnLongClickListener(v -> {
            showAppOptions(app);
            return true;
        });
        card.setOnFocusChangeListener(focusFx);
        return card;
    }

    private View createInputCard(String labelText) {
        View card = getLayoutInflater().inflate(R.layout.item_input_card, null);
        TextView label = card.findViewById(R.id.card_label);
        label.setText(labelText);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.card_width),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int m = getResources().getDimensionPixelSize(R.dimen.card_margin);
        lp.setMargins(m, m, m, m);
        card.setLayoutParams(lp);

        card.setOnFocusChangeListener(focusFx);
        return card;
    }

    private void addInputCards(LinearLayout row) {
        List<HdmiInputHelper.Entry> inputs = HdmiInputHelper.getInputs(this);
        if (inputs.isEmpty()) {
            // Platform exposes no TV inputs: show labelled placeholders that
            // open a documented input-selection dialog instead of dead ends.
            String[] fallbacks = {"HDMI 1", "HDMI 2", "HDMI 3", "Live TV", "AV"};
            for (String name : fallbacks) {
                View card = createInputCard(name);
                card.setOnClickListener(v -> showInputUnavailableDialog());
                row.addView(card);
            }
            return;
        }
        for (final HdmiInputHelper.Entry e : inputs) {
            View card = createInputCard(e.label);
            card.setOnClickListener(v -> onInputSelected(e));
            row.addView(card);
        }
    }

    private void onInputSelected(HdmiInputHelper.Entry e) {
        if (HdmiInputHelper.launchPassthrough(this, e)) return;
        if (HdmiInputHelper.launchSetup(this, e)) return;
        showInputUnavailableDialog();
    }

    private void showInputUnavailableDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.inputs_unavailable_title)
                .setMessage(R.string.inputs_unavailable_msg)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ------------------------------------------------------------------ actions

    private void launchApp(AppInfo app) {
        try {
            startActivity(app.launchIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.app_not_available, app.label),
                    Toast.LENGTH_SHORT).show();
            rebuildRows();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.launch_failed, app.label),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showAppOptions(final AppInfo app) {
        String[] options = {getString(R.string.option_move_left),
                getString(R.string.option_move_right)};
        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(options, (dialog, which) -> moveStreamingApp(app.packageName, which == 0 ? -1 : 1))
                .show();
    }

    private void moveStreamingApp(String pkg, int delta) {
        int idx = displayedStreamingPackages.indexOf(pkg);
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= displayedStreamingPackages.size()) return;
        List<String> order = new ArrayList<>(displayedStreamingPackages);
        order.remove(idx);
        order.add(target, pkg);
        prefs.saveStreamingOrder(order);
        rebuildRows();
        Toast.makeText(this, R.string.order_saved, Toast.LENGTH_SHORT).show();
    }

    private void startActivitySafely(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.action_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        startActivitySafely(new Intent(Settings.ACTION_SETTINGS));
    }

    // ------------------------------------------------------------------ clock

    private void updateClock() {
        if (clockView == null) return;
        Date now = new Date();
        clockView.setText(getString(R.string.clock_format,
                timeFmt.format(now), dateFmt.format(now)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateClock();
        handler.post(clockTick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(clockTick);
        super.onPause();
    }

    // ------------------------------------------------------------------ D-pad

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            return moveVertical(1) || super.onKeyDown(keyCode, event);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            return moveVertical(-1) || super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    /** Stay home: Back resets focus to the top of the screen instead of finishing. */
    @Override
    public void onBackPressed() {
        View first = rowsContainer;
        if (rowsContainer != null && rowsContainer.getChildCount() > 1) {
            View scroller = rowsContainer.getChildAt(1);
            if (scroller instanceof HorizontalScrollView) {
                View content = ((HorizontalScrollView) scroller).getChildAt(0);
                if (content instanceof LinearLayout && ((LinearLayout) content).getChildCount() > 0) {
                    ((LinearLayout) content).getChildAt(0).requestFocus();
                    return;
                }
            }
        }
        super.onBackPressed();
    }

    private boolean moveVertical(int direction) {
        View focused = getCurrentFocus();
        if (focused == null) return false;

        int zoneIndex = -1;
        int childIndex = 0;
        for (int i = 0; i < focusZones.size(); i++) {
            Object zone = focusZones.get(i);
            if (zone instanceof View && focused == zone) {
                zoneIndex = i;
                break;
            }
            if (zone instanceof LinearLayout && isDescendant((ViewGroup) zone, focused)) {
                zoneIndex = i;
                childIndex = indexWithin((LinearLayout) zone, focused);
                break;
            }
        }
        if (zoneIndex < 0) return false;

        int targetIndex = zoneIndex + direction;
        if (targetIndex < 0 || targetIndex >= focusZones.size()) return false;

        Object target = focusZones.get(targetIndex);
        if (target instanceof View) {
            ((View) target).requestFocus();
            return true;
        }

        LinearLayout row = (LinearLayout) target;
        if (row.getChildCount() == 0) return false;
        int targetChild = Math.max(0, Math.min(childIndex, row.getChildCount() - 1));
        View child = row.getChildAt(targetChild);
        child.requestFocus();
        ensureVisible(child);
        return true;
    }

    private boolean isDescendant(ViewGroup group, View v) {
        ViewParent p = v.getParent();
        while (p != null) {
            if (p == group) return true;
            p = p.getParent();
        }
        return false;
    }

    private int indexWithin(LinearLayout row, View child) {
        for (int i = 0; i < row.getChildCount(); i++) {
            if (row.getChildAt(i) == child) return i;
        }
        return 0;
    }

    private void ensureVisible(View child) {
        ViewParent p = child.getParent();
        while (p != null && !(p instanceof HorizontalScrollView)) p = p.getParent();
        if (p instanceof HorizontalScrollView) {
            HorizontalScrollView scroller = (HorizontalScrollView) p;
            int offset = Math.max(0, child.getLeft()
                    - getResources().getDimensionPixelSize(R.dimen.card_margin));
            scroller.smoothScrollTo(offset, 0);
        }
    }
}
