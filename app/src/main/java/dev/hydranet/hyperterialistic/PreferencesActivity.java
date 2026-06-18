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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
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
        private static final String DOWNLOADS_CHANNEL_ID = "downloads";
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
                    updateNotificationWarning();
                    return true;
                });
            }
            Preference notificationWarning =
                    findPreference(getString(R.string.pref_offline_notification_warning));
            if (notificationWarning != null) {
                notificationWarning.setOnPreferenceClickListener(preference -> {
                    enableNotifications();
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
            updateNotificationWarning();
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

        private void enableNotifications() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionIfNeeded();
                return;
            }
            openNotificationSettings();
        }

        private void openNotificationSettings() {
            Intent intent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
                if (isDownloadsChannelDisabled()) {
                    intent.putExtra(Settings.EXTRA_CHANNEL_ID, DOWNLOADS_CHANNEL_ID);
                }
            } else {
                intent = getApplicationDetailsSettingsIntent();
            }
            if (intent.resolveActivity(requireContext().getPackageManager()) == null) {
                intent = getApplicationDetailsSettingsIntent();
            }
            startActivity(intent);
        }

        private Intent getApplicationDetailsSettingsIntent() {
            return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + requireContext().getPackageName()));
        }

        private void updateNotificationWarning() {
            Preference notificationWarning =
                    findPreference(getString(R.string.pref_offline_notification_warning));
            if (notificationWarning != null) {
                notificationWarning.setVisible(!canPostDownloadNotifications());
            }
        }

        private boolean canPostDownloadNotifications() {
            Context context = requireContext();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context,
                            Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                return false;
            }
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isDownloadsChannelDisabled();
        }

        private boolean isDownloadsChannelDisabled() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return false;
            }
            NotificationManager notificationManager =
                    (NotificationManager) requireContext()
                            .getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel =
                    notificationManager.getNotificationChannel(DOWNLOADS_CHANNEL_ID);
            return channel != null &&
                    channel.getImportance() == NotificationManager.IMPORTANCE_NONE;
        }

        @Override
        public void onResume() {
            super.onResume();
            if (getArguments().getInt(EXTRA_PREFERENCES) == R.xml.preferences_offline) {
                updateNotificationWarning();
            }
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
                updateNotificationWarning();
                return;
            }
            TwoStatePreference progressNotification =
                    findPreference(getString(R.string.pref_offline_notification));
            if (progressNotification != null) {
                progressNotification.setChecked(false);
            }
            updateNotificationWarning();
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
