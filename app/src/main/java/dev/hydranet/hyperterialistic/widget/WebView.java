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

package dev.hydranet.hyperterialistic.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;

import dev.hydranet.hyperterialistic.annotation.Synthetic;

public class WebView extends android.webkit.WebView {
    static final String BLANK = "about:blank";
    static final String FILE = "file:///";
    private final HistoryWebViewClient mClient = new HistoryWebViewClient();
    @Synthetic String mPendingUrl, mPendingHtml;
    @Synthetic boolean mBlankLoaded;

    public WebView(Context context) {
        this(context, null);
    }

    public WebView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setWebViewClient(mClient);
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        mClient.wrap(client);
    }

    @Override
    public boolean canGoBack() {
        return TextUtils.isEmpty(mPendingUrl) && super.canGoBack();
    }

    public void reloadUrl(String url) {
        if (getProgress() < 100) {
            stopLoading(); // this will fire onPageFinished for current URL
        }
        mPendingUrl = url;
        mBlankLoaded = false;
        loadUrl(BLANK); // clear current web resources, load pending URL upon onPageFinished
    }

    public void reloadHtml(String html) {
        mPendingHtml = html;
        reloadUrl(FILE);
    }

    static class HistoryWebViewClient extends WebViewClient {
        private WebViewClient mClient;

        @Override
        public void onPageStarted(android.webkit.WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            view.pageUp(true);
            // Reveal the WebView as soon as real content starts loading. Keying this on an exact
            // URL match against the pending URL was fragile: redirects, HSTS http->https upgrades
            // and URL encoding make the started URL differ from what we requested, leaving the
            // view stuck INVISIBLE (a blank white screen) even though the page loaded fine. Any
            // non-blank page is real content, so show it.
            if (!TextUtils.equals(url, BLANK)) {
                view.setVisibility(VISIBLE);
            }
            if (mClient != null) {
                mClient.onPageStarted(view, url, favicon);
            }
        }

        @Override
        public void onPageFinished(android.webkit.WebView view, String url) {
            super.onPageFinished(view, url);
            WebView webView = (WebView) view;
            if (TextUtils.equals(url, BLANK)) { // has pending reload, open corresponding URL
                webView.mBlankLoaded = true;
                if (!TextUtils.isEmpty(webView.mPendingHtml)) {
                    view.loadDataWithBaseURL(webView.mPendingUrl, webView.mPendingHtml,
                            "text/html", "UTF-8", webView.mPendingUrl);
                } else {
                    view.loadUrl(webView.mPendingUrl);
                }
            } else if (!TextUtils.isEmpty(webView.mPendingUrl) && webView.mBlankLoaded) {
                // The pending reload's real page finished. Don't require an exact URL match:
                // redirects, HSTS http->https upgrades and URL encoding routinely change the
                // final URL, and a mismatch here left mPendingUrl set forever - breaking
                // canGoBack() and skipping clearHistory(). The blank interstitial having
                // loaded is what distinguishes this from a stale finish of the previous page
                // (e.g. the one stopLoading() fires during reloadUrl()).
                webView.mPendingUrl = null;
                webView.mPendingHtml = null;
                webView.mBlankLoaded = false;
                view.clearHistory();
            }
            // Safety net: a finished non-blank page is real content, so make sure it's visible
            // even if onPageStarted was missed or the URL changed during the load.
            if (!TextUtils.equals(url, BLANK)) {
                view.setVisibility(VISIBLE);
            }
            if (mClient != null) {
                mClient.onPageFinished(view, url);
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(android.webkit.WebView view, String url) {
            releaseCacheOnlyLock(view, url);
            return mClient != null ? mClient.shouldOverrideUrlLoading(view, url) :
                    super.shouldOverrideUrlLoading(view, url);
        }

        @TargetApi(Build.VERSION_CODES.N)
        @Override
        public boolean shouldOverrideUrlLoading(android.webkit.WebView view,
                                                WebResourceRequest request) {
            releaseCacheOnlyLock(view, request.getUrl().toString());
            return mClient != null ? mClient.shouldOverrideUrlLoading(view, request) :
                    super.shouldOverrideUrlLoading(view, request);
        }

        // Serving a saved archive pins the WebView to LOAD_CACHE_ONLY (CacheableWebView). That
        // must not leak into user navigation: clicking a link inside an archived page would
        // fail with a cache miss even when online. This hook only fires for user/JS-initiated
        // navigation, never for our own loadUrl() calls, so the archive load itself keeps its
        // cache-only mode.
        private void releaseCacheOnlyLock(android.webkit.WebView view, String url) {
            if (URLUtil.isNetworkUrl(url) &&
                    view.getSettings().getCacheMode() == WebSettings.LOAD_CACHE_ONLY) {
                view.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
            }
        }

        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        @SuppressWarnings("deprecation")
        @Override
        public WebResourceResponse shouldInterceptRequest(android.webkit.WebView view, String url) {
            return mClient != null ? mClient.shouldInterceptRequest(view, url) :
                    super.shouldInterceptRequest(view, url);
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public WebResourceResponse shouldInterceptRequest(android.webkit.WebView view, WebResourceRequest request) {
            return mClient != null ? mClient.shouldInterceptRequest(view, request) :
                    super.shouldInterceptRequest(view, request);
        }

        @Synthetic
        void wrap(WebViewClient client) {
            mClient = client;
        }
    }
}
