/*
 * Copyright (c) 2016 Ha Duy Trung
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.hydranet.hyperterialistic;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.Toast;

import dev.hydranet.hyperterialistic.data.OfflineCacheManager;
import dev.hydranet.hyperterialistic.data.SyncScheduler;

public class PreferencesActivity extends ThemedActivity {
    public static final String EXTRA_TITLE = PreferencesActivity.class.getName() + ".EXTRA_TITLE";
    public static final String EXTRA_PREFERENCES = PreferencesActivity.class.getName() + ".EXTRA_PREFERENCES";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);
        setTitle(getIntent().getIntExtra(EXTRA_TITLE, 0));
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        applyStatusBarInsets(findViewById(R.id.status_bar_spacer));
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME |
                ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);
        if (savedInstanceState == null) {
            Bundle args = new Bundle();
            args.putInt(EXTRA_PREFERENCES, getIntent().getIntExtra(EXTRA_PREFERENCES, 0));
            getSupportFragmentManager()
                    .beginTransaction()
                    .add(R.id.content_frame,
                            Fragment.instantiate(this, SettingsFragment.class.getName(), args),
                            SettingsFragment.class.getName())
                    .commit();
        }
    }

    private void applyStatusBarInsets(View statusBarSpacer) {
        ViewCompat.setOnApplyWindowInsetsListener(statusBarSpacer, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams.height != top) {
                layoutParams.height = top;
                view.setLayoutParams(layoutParams);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(statusBarSpacer);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private static final int REQUEST_POST_NOTIFICATIONS = 1;
        private final Handler mHandler = new Handler(Looper.getMainLooper());

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Preferences.sync(getPreferenceManager());
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(getArguments().getInt(EXTRA_PREFERENCES));
            if (getArguments().getInt(EXTRA_PREFERENCES) == R.xml.preferences_offline) {
                bindOfflineCachePreferences();
            }
        }

        private void bindOfflineCachePreferences() {
            Preference progressNotification =
                    findPreference(getString(R.string.pref_offline_notification));
            if (progressNotification != null) {
                progressNotification.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (Boolean.TRUE.equals(newValue)) {
                        requestNotificationPermissionIfNeeded();
                    }
                    return true;
                });
            }
            Preference refreshCache = findPreference(getString(R.string.pref_refresh_offline_cache));
            if (refreshCache != null) {
                refreshCache.setOnPreferenceClickListener(preference -> {
                    refreshOfflineCache();
                    return true;
                });
            }
            Preference clearCache = findPreference(getString(R.string.pref_clear_offline_cache));
            if (clearCache != null) {
                clearCache.setOnPreferenceClickListener(preference -> {
                    confirmClearOfflineCache();
                    return true;
                });
            }
            updateOfflineCacheStats();
        }

        private void requestNotificationPermissionIfNeeded() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED) {
                return;
            }
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_POST_NOTIFICATIONS);
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                               @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            if (requestCode != REQUEST_POST_NOTIFICATIONS) {
                return;
            }
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                return;
            }
            TwoStatePreference progressNotification =
                    findPreference(getString(R.string.pref_offline_notification));
            if (progressNotification != null) {
                progressNotification.setChecked(false);
            }
        }

        private void refreshOfflineCache() {
            if (SyncScheduler.scheduleHotCacheNow(requireContext())) {
                Toast.makeText(requireContext(), R.string.offline_cache_refresh_queued,
                        Toast.LENGTH_SHORT).show();
            }
        }

        private void confirmClearOfflineCache() {
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setMessage(R.string.confirm_clear_offline_cache)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> clearOfflineCache())
                    .show();
        }

        private void clearOfflineCache() {
            Context appContext = requireContext().getApplicationContext();
            new WebView(requireContext()).clearCache(true);
            new Thread(() -> {
                new OfflineCacheManager(appContext).clear();
                mHandler.post(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(requireContext(), R.string.offline_cache_cleared,
                            Toast.LENGTH_SHORT).show();
                    updateOfflineCacheStats();
                });
            }, "clear-offline-cache").start();
        }

        private void updateOfflineCacheStats() {
            Preference stats = findPreference(getString(R.string.pref_offline_cache_stats));
            if (stats == null) {
                return;
            }
            stats.setSummary(R.string.offline_cache_calculating);
            Context appContext = requireContext().getApplicationContext();
            new Thread(() -> {
                OfflineCacheManager.Stats cacheStats =
                        new OfflineCacheManager(appContext).getStats();
                mHandler.post(() -> {
                    if (isAdded()) {
                        stats.setSummary(cacheStats.format(requireContext()));
                    }
                });
            }, "offline-cache-stats").start();
        }
    }
}
