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

package dev.hydranet.hyperterialistic.data;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.format.DateUtils;
import androidx.annotation.Nullable;

import dev.hydranet.hyperterialistic.Preferences;
import dev.hydranet.hyperterialistic.widget.AdBlockWebViewClient;
import dev.hydranet.hyperterialistic.widget.CacheableWebView;

public class WebCacheService extends Service {
    static final String EXTRA_URL = "extra:url";
    // A page that never finishes loading (spotty link) must not keep the service - and its
    // WebView - alive forever; give up and let the next sync pass retry.
    private static final long LOAD_TIMEOUT_MILLIS = 2 * DateUtils.MINUTE_IN_MILLIS;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { // restarted
            stopSelf(startId);
            return START_STICKY;
        }
        String url = intent.getStringExtra(EXTRA_URL);
        if (ArticleCache.contains(this, url)) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        CacheableWebView webView = new CacheableWebView(this);
        // Timers are global across WebViews and may have been paused when the user left the
        // app; without resuming them this invisible WebView never finishes loading.
        webView.resumeTimers();
        webView.setWebViewClient(new AdBlockWebViewClient(Preferences.adBlockEnabled(this)));
        Runnable timeout = () -> {
            webView.stopLoading();
            stopSelf(startId);
        };
        webView.setWebChromeClient(new CacheableWebView.ArchiveClient() {
            @Override
            protected void onArchiveSaved(String fileName) {
                // Stop only once the archive is written and validated: stopping at progress
                // 100 raced the async saveWebArchive and could kill the process mid-write.
                mHandler.removeCallbacks(timeout);
                stopSelf(startId);
            }
        });
        mHandler.postDelayed(timeout, LOAD_TIMEOUT_MILLIS);
        webView.loadUrl(url);
        return START_STICKY;
    }
}
