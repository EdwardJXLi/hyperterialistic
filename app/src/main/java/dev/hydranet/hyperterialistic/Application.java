/*
 * Copyright (c) 2015 Ha Duy Trung
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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.StrictMode;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import dagger.ObjectGraph;
import dev.hydranet.hyperterialistic.data.AlgoliaClient;
import dev.hydranet.hyperterialistic.data.SyncScheduler;
import rx.schedulers.Schedulers;

public class Application extends android.app.Application implements Injectable {

    public static Typeface TYPE_FACE = null;
    private ObjectGraph mApplicationGraph;
    private SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        mApplicationGraph = ObjectGraph.create();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setDefaultNightMode(Preferences.Theme.getAutoDayNightMode(this));
        AlgoliaClient.sSortByTime = Preferences.isSortByRecent(this);
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyFlashScreen()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }
        Preferences.migrate(this);
        TYPE_FACE = FontCache.getInstance().get(this, Preferences.Theme.getTypeface(this));
        AppUtils.registerAccountsUpdatedListener(this);
        AdBlocker.init(this, Schedulers.io());
        SyncScheduler.scheduleHotCache(this);
        mPreferenceChangeListener = (sharedPreferences, key) -> {
            if (key == null ||
                    key.equals(getString(R.string.pref_hot_cache)) ||
                    key.equals(getString(R.string.pref_hot_cache_count)) ||
                    key.equals(getString(R.string.pref_hot_cache_frequency)) ||
                    key.equals(getString(R.string.pref_offline_comments)) ||
                    key.equals(getString(R.string.pref_offline_article)) ||
                    key.equals(getString(R.string.pref_offline_readability)) ||
                    key.equals(getString(R.string.pref_offline_data))) {
                SyncScheduler.scheduleHotCache(this);
            }
        };
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
    }

    @Override
    public void inject(Object object) {
        getApplicationGraph().inject(object);
    }

    @Override
    public ObjectGraph getApplicationGraph() {
        return mApplicationGraph;
    }
}
