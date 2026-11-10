package com.atelier.tvlauncher;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Simple, D-pad friendly list of every installed leanback app. */
public class AllAppsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_apps);

        final List<AppInfo> apps = AppDiscovery.discoverLeanbackApps(this);
        ListView list = findViewById(R.id.apps_list);
        list.setAdapter(new AppsAdapter(apps));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                try {
                    startActivity(apps.get(position).launchIntent);
                } catch (Exception e) {
                    Toast.makeText(AllAppsActivity.this, R.string.launch_failed_generic,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private class AppsAdapter extends BaseAdapter {
        private final List<AppInfo> apps;
        private final LayoutInflater inflater;

        AppsAdapter(List<AppInfo> apps) {
            this.apps = apps;
            this.inflater = LayoutInflater.from(AllAppsActivity.this);
        }

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = inflater.inflate(R.layout.item_app_list, parent, false);
            }
            AppInfo app = apps.get(position);
            ImageView icon = v.findViewById(R.id.item_icon);
            TextView label = v.findViewById(R.id.item_label);
            icon.setImageDrawable(app.icon);
            label.setText(app.label);
            return v;
        }
    }
}
