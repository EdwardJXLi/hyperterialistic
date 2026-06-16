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

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceFragmentCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

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

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Preferences.sync(getPreferenceManager());
        }

        @Override
        public void onCreatePreferences(Bundle bundle, String s) {
            addPreferencesFromResource(getArguments().getInt(EXTRA_PREFERENCES));
        }
    }
}
