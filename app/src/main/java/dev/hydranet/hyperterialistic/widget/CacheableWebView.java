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

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.CallSuper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

public class CacheableWebView extends WebView {
    private static final String CACHE_PREFIX = "webarchive-";
    private static final String CACHE_EXTENSION = ".mht";
    private static final String ARCHIVE_DIR = "webarchives";
    private static final String SNAPSHOT_LOCATION_HEADER = "Snapshot-Content-Location:";
    private ArchiveClient mArchiveClient = new ArchiveClient();

    public CacheableWebView(Context context) {
        this(context, null);
    }

    public CacheableWebView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CacheableWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Override
    public void reloadUrl(String url) {
        super.reloadUrl(getCacheableUrl(url));
    }

    @Override
    public void loadUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        mArchiveClient.lastProgress = 0;
        super.loadUrl(getCacheableUrl(url));
    }

    @Override
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        mArchiveClient.lastProgress = 0;
        super.loadUrl(getCacheableUrl(url), additionalHttpHeaders);
    }

    @Override
    public void setWebChromeClient(WebChromeClient client) {
        if (!(client instanceof ArchiveClient)) {
            throw new IllegalArgumentException("client should be an instance of " +
                    ArchiveClient.class.getName());
        }
        mArchiveClient = (ArchiveClient) client;
        super.setWebChromeClient(mArchiveClient);
    }

    private void init() {
        enableCache();
        setLoadSettings();
        setWebViewClient(new WebViewClient());
        setWebChromeClient(mArchiveClient);
    }

    private void enableCache() {
        WebSettings webSettings = getSettings();
        webSettings.setAllowFileAccess(true);
        // Normal loading with HTTP caching; getCacheableUrl() switches to a saved archive when one
        // exists. We never pin the WebView to LOAD_CACHE_ONLY for a live URL — that only produces
        // net::ERR_CACHE_MISS when the document isn't already in the WebView's cache.
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setLoadSettings() {
        WebSettings webSettings = getSettings();
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setJavaScriptEnabled(true);
        // Without DOM storage, sites that touch localStorage/sessionStorage during their initial
        // render (most client-rendered pages) throw and paint nothing — a blank white page with
        // no error.
        webSettings.setDomStorageEnabled(true);
    }

    private String getCacheableUrl(String url) {
        if (TextUtils.equals(url, BLANK)) {
            return url;
        }
        if (URLUtil.isFileUrl(url) || URLUtil.isDataUrl(url)) {
            // Local content (a saved archive, the bundled PDF viewer, wrapped HTML): nothing to
            // archive here, and generating a cache name for it made onProgressChanged snapshot
            // the local content itself into a junk cache entry.
            mArchiveClient.cacheFileName = null;
            return url;
        }
        mArchiveClient.cacheFileName = generateCacheFilename(url);
        File cacheFile = new File(mArchiveClient.cacheFileName);
        if (cacheFile.exists()) {
            if (isValidArchive(cacheFile)) {
                // Serve the saved archive: works offline, loads fast, and avoids re-fetching. A
                // file:// URL loads regardless of cache mode. Nothing to archive during this
                // load - and if the user later clicks a link out of the archive, a stale
                // cacheFileName would let that page overwrite this article's archive.
                mArchiveClient.cacheFileName = null;
                getSettings().setCacheMode(WebSettings.LOAD_CACHE_ONLY);
                return Uri.fromFile(cacheFile).toString();
            }
            // A snapshot of something other than the page (about:blank interstitial, error
            // page, truncated write): serving it shows a blank article forever. Drop it and
            // load fresh; a good archive gets saved once the page loads.
            //noinspection ResultOfMethodCallIgnored
            cacheFile.delete();
        }
        // No saved archive: load from the network (Chromium still serves fresh entries from its
        // HTTP cache). When actually offline this fails with an honest connectivity error rather
        // than a confusing cache miss.
        getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        return url;
    }

    // A usable archive is a Blink MHTML snapshot of a real web page, recorded in its
    // Snapshot-Content-Location header. Snapshots of anything else (about:blank,
    // chrome-error://…) render as an empty page.
    static boolean isValidArchive(File file) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            for (int i = 0; i < 10; i++) {
                String line = reader.readLine();
                if (line == null) {
                    return false;
                }
                if (line.startsWith(SNAPSHOT_LOCATION_HEADER)) {
                    return URLUtil.isNetworkUrl(
                            line.substring(SNAPSHOT_LOCATION_HEADER.length()).trim());
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private String generateCacheFilename(String url) {
        return getArchiveFile(getContext(), url).getAbsolutePath();
    }

    // Archives are the offline reading feature, not a disposable cache: under storage
    // pressure installd purges files in getCacheDir() (observed wiping every archive minutes
    // after the hot cache saved them), so they live in filesDir instead. Growth is bounded by
    // OfflineCacheManager.garbageCollect(), which retains only the current hot-cache lists
    // and saved stories.
    public static File getArchiveDir(Context context) {
        return new File(context.getApplicationContext().getFilesDir(), ARCHIVE_DIR);
    }

    public static File getArchiveFile(Context context, String url) {
        File dir = getArchiveDir(context);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File file = new File(dir, CACHE_PREFIX + url.hashCode() + CACHE_EXTENSION);
        if (!file.exists()) {
            // Rescue any archive an older version saved into the purgeable cache dir.
            File legacy = new File(context.getApplicationContext().getCacheDir(), file.getName());
            if (legacy.exists()) {
                //noinspection ResultOfMethodCallIgnored
                legacy.renameTo(file);
            }
        }
        return file;
    }

    public static boolean hasValidArchive(Context context, String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        File file = getArchiveFile(context, url);
        return file.exists() && isValidArchive(file);
    }

    public static boolean isArchiveFile(File file) {
        return file != null && file.isFile() &&
                file.getName().startsWith(CACHE_PREFIX) &&
                file.getName().endsWith(CACHE_EXTENSION);
    }

    public static class ArchiveClient extends WebChromeClient {
        int lastProgress = 0;
        String cacheFileName = null;

        @CallSuper
        @Override
        public void onProgressChanged(android.webkit.WebView view, int newProgress) {
            if (view.getSettings().getCacheMode() == WebSettings.LOAD_CACHE_ONLY) {
                return;
            }
            // Progress also reaches 100 for the about:blank interstitial that reloadUrl()
            // inserts before each real load. Snapshotting that moment poisoned the cache with
            // an empty page, which was then served instead of the network on every later open
            // — a permanently blank article. Only a loaded http(s) page is worth archiving.
            if (!URLUtil.isNetworkUrl(view.getUrl())) {
                return;
            }
            if (cacheFileName != null && lastProgress != 100 && newProgress == 100) {
                lastProgress = newProgress;
                saveArchive(view, cacheFileName);
            }
        }

        private void saveArchive(android.webkit.WebView view, final String fileName) {
            // The callback form tells us whether the write actually happened. Snapshots of an
            // error page pass the network-URL check above (a failed navigation keeps the
            // requested URL) but fail isValidArchive; keeping one around would be served as
            // the page forever, i.e. a permanently blank article.
            view.saveWebArchive(fileName, false, value -> {
                File file = new File(fileName);
                if (value == null || !isValidArchive(file)) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                } else {
                    onArchiveSaved(fileName);
                }
            });
        }

        /**
         * Called once a page archive has been written and validated. Unlike progress reaching
         * 100, this means the page is actually available offline.
         */
        protected void onArchiveSaved(String fileName) {
            // override to handle archive completion
        }
    }
}
