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
import android.os.IBinder;
import androidx.annotation.Nullable;
import android.webkit.WebView;

import dev.hydranet.hyperterialistic.Preferences;
import dev.hydranet.hyperterialistic.widget.AdBlockWebViewClient;
import dev.hydranet.hyperterialistic.widget.CacheableWebView;

public class WebCacheService extends Service {
    static final String EXTRA_URL = "extra:url";

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
        webView.setWebViewClient(new AdBlockWebViewClient(Preferences.adBlockEnabled(this)));
        webView.setWebChromeClient(new CacheableWebView.ArchiveClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (newProgress == 100) {
                    ArticleCache.put(WebCacheService.this, url);
                    stopSelf(startId);
                }
            }
        });
        webView.loadUrl(url);
        return START_STICKY;
    }
}
