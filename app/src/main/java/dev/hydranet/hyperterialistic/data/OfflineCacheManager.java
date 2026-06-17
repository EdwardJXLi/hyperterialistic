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

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.Formatter;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.hydranet.hyperterialistic.R;
import dev.hydranet.hyperterialistic.widget.CacheableWebView;

public class OfflineCacheManager {
    private static final int MAX_SCAN_BYTES = 256 * 1024;

    private final Context mContext;
    private final MaterialisticDatabase mDatabase;

    public OfflineCacheManager(Context context) {
        mContext = context.getApplicationContext();
        mDatabase = MaterialisticDatabase.getInstance(mContext);
    }

    public Stats getStats() {
        Stats stats = new Stats();
        scanOkHttpCache(mContext.getCacheDir(), stats);
        stats.readability = mDatabase.getReadableDao().count();
        stats.bytes = size(mContext.getCacheDir()) +
                size(getDataDirChild("app_webview")) +
                size(getDataDirChild("app_webview_zygote")) +
                size(mContext.getDatabasePath("Materialistic.db")) +
                size(mContext.getDatabasePath("Materialistic.db-wal")) +
                size(mContext.getDatabasePath("Materialistic.db-shm"));
        return stats;
    }

    public void clear() {
        deleteContents(mContext.getCacheDir());
        deleteContents(getDataDirChild("app_webview"));
        deleteContents(getDataDirChild("app_webview_zygote"));
        ArticleCache.clear(mContext);
        HackerNewsItemCache.clear(mContext);
        StoryListCache.clear(mContext);
        mDatabase.getReadableDao().deleteAll();
    }

    public void garbageCollect() {
        RetainedCache retainedCache = collectRetainedCache();
        HackerNewsItemCache.retainOnly(mContext, retainedCache.itemIds);
        ArticleCache.retainOnly(mContext, retainedCache.articleUrls);
        deleteStaleWebArchives(mContext.getCacheDir(), retainedCache.articleUrls);
        if (retainedCache.itemIds.isEmpty()) {
            mDatabase.getReadableDao().deleteAll();
        } else {
            mDatabase.getReadableDao().deleteExcept(new ArrayList<>(retainedCache.itemIds));
        }
    }

    private RetainedCache collectRetainedCache() {
        RetainedCache retainedCache = new RetainedCache();
        collectStoryList(retainedCache, ItemManager.TOP_FETCH_MODE);
        collectStoryList(retainedCache, ItemManager.BEST_FETCH_MODE);
        List<String> savedItemIds = mDatabase.getSavedStoriesDao().selectItemIds();
        for (String itemId : savedItemIds) {
            collectItemTree(retainedCache, itemId);
        }
        return retainedCache;
    }

    private void collectStoryList(RetainedCache retainedCache, @ItemManager.FetchMode String filter) {
        int[] itemIds = StoryListCache.get(mContext, filter);
        if (itemIds == null) {
            return;
        }
        for (int itemId : itemIds) {
            collectItemTree(retainedCache, String.valueOf(itemId));
        }
    }

    private void collectItemTree(RetainedCache retainedCache, String itemId) {
        if (TextUtils.isEmpty(itemId) || !retainedCache.itemIds.add(itemId)) {
            return;
        }
        HackerNewsItem item = HackerNewsItemCache.get(mContext, itemId);
        if (item == null) {
            return;
        }
        if (item.isStoryType() && !TextUtils.isEmpty(item.getUrl())) {
            retainedCache.articleUrls.add(item.getUrl());
        }
        if (item.getKids() == null) {
            return;
        }
        for (long kid : item.getKids()) {
            collectItemTree(retainedCache, String.valueOf(kid));
        }
    }

    private void deleteStaleWebArchives(File dir, Set<String> retainedUrls) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        Set<String> retainedArchiveNames = new HashSet<>();
        for (String url : retainedUrls) {
            retainedArchiveNames.add(CacheableWebView.getArchiveFile(mContext, url).getName());
        }
        deleteStaleWebArchives(dir, retainedArchiveNames);
    }

    private void deleteStaleWebArchives(File dir, Set<String> retainedArchiveNames) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                deleteStaleWebArchives(file, retainedArchiveNames);
            } else if (CacheableWebView.isArchiveFile(file) &&
                    !retainedArchiveNames.contains(file.getName())) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private void scanOkHttpCache(File dir, Stats stats) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                scanOkHttpCache(file, stats);
                continue;
            }
            countCachedItem(file, stats);
        }
    }

    private void countCachedItem(File file, Stats stats) {
        String body = readPrefix(file);
        if (!body.contains("\"id\"") || !body.contains("\"type\"")) {
            return;
        }
        if (body.contains("\"type\":\"story\"")) {
            stats.stories++;
        } else if (body.contains("\"type\":\"comment\"")) {
            stats.comments++;
        } else if (body.contains("\"type\":\"job\"") || body.contains("\"type\":\"poll\"") ||
                body.contains("\"type\":\"pollopt\"")) {
            stats.otherItems++;
        }
    }

    @NonNull
    private String readPrefix(File file) {
        if (!file.isFile() || file.length() == 0 || file.length() > MAX_SCAN_BYTES) {
            return "";
        }
        int length = (int) Math.min(file.length(), MAX_SCAN_BYTES);
        byte[] bytes = new byte[length];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int read = inputStream.read(bytes);
            return read > 0 ? new String(bytes, 0, read) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private File getDataDirChild(String name) {
        File dataDir = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ?
                mContext.getDataDir() :
                new File(mContext.getApplicationInfo().dataDir);
        return new File(dataDir, name);
    }

    private long size(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return file.length();
        }
        long total = 0L;
        File[] children = file.listFiles();
        if (children == null) {
            return total;
        }
        for (File child : children) {
            total += size(child);
        }
        return total;
    }

    private void deleteContents(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                deleteContents(file);
            }
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    public static class Stats {
        public int stories;
        public int comments;
        public int otherItems;
        public int readability;
        public long bytes;

        public String format(Context context) {
            return context.getString(R.string.offline_cache_stats,
                    stories,
                    comments,
                    readability,
                    otherItems,
                    Formatter.formatFileSize(context, bytes));
        }
    }

    private static class RetainedCache {
        final Set<String> itemIds = new LinkedHashSet<>();
        final Set<String> articleUrls = new HashSet<>();
    }
}
