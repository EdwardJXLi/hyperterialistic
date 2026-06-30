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

import android.annotation.TargetApi;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.TextUtils;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import dev.hydranet.hyperterialistic.ActivityModule;
import dev.hydranet.hyperterialistic.Injectable;
import dev.hydranet.hyperterialistic.Preferences;
import dev.hydranet.hyperterialistic.R;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class HotCacheJobService extends JobService {
    private static final int NOTIFICATION_ID = Integer.MAX_VALUE - 2;
    private static final String DOWNLOADS_CHANNEL_ID = "downloads";
    // Fraction of story bodies that must actually be cached before we advance the offline
    // story list and garbage collect the previous one. Keeps a spotty connection from
    // replacing a working offline set with empty placeholder rows.
    private static final double COMMIT_THRESHOLD = 0.8;

    @Inject RestServiceFactory mFactory;
    @Inject ReadabilityClient mReadabilityClient;
    @Inject LocalCache mLocalCache;
    @Inject okhttp3.Cache mHttpCache;
    private volatile Thread mThread;
    private NotificationManager mNotificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        ((Injectable) getApplication())
                .getApplicationGraph()
                .plus(new ActivityModule(this))
                .inject(this);
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(DOWNLOADS_CHANNEL_ID,
                    getString(R.string.notification_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW);
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!Preferences.Offline.isHotCacheEnabled(this) ||
                !Preferences.Offline.currentConnectionEnabled(this)) {
            jobFinished(params, false);
            return false;
        }
        mThread = new Thread(() -> {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            if (cacheHotStories()) {
                new OfflineCacheManager(this, mHttpCache).garbageCollect();
            }
            cancelProgress();
            jobFinished(params, false);
        }, "hot-cache-sync");
        mThread.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Thread thread = mThread;
        if (thread != null) {
            thread.interrupt();
            mThread = null;
        }
        cancelProgress();
        return true;
    }

    private boolean cacheHotStories() {
        HackerNewsClient.RestService service = mFactory.create(HackerNewsClient.BASE_API_URL,
                HackerNewsClient.RestService.class);
        int limit = Preferences.Offline.getHotCacheCount(this);
        int[] topStories = networkTopStories(service);
        int[] bestStories = networkBestStories(service);
        Set<Integer> ids = new LinkedHashSet<>();
        addStories(ids, topStories, limit);
        addStories(ids, bestStories, limit);
        if (ids.isEmpty()) {
            // Story-list fetch failed; keep the existing offline set untouched.
            return false;
        }
        updateProgress(0, 0, true);
        int total = ids.size();
        int downloaded = 0;
        int storiesCached = 0;
        for (Integer id : ids) {
            if (Thread.currentThread().isInterrupted()) {
                return false;
            }
            String storyId = String.valueOf(id);
            if (!isCached(service, storyId)) {
                syncStory(storyId);
            }
            if (cachedItem(service, storyId) != null) {
                storiesCached++;
            }
            downloaded++;
            updateProgress(downloaded, total, false);
        }
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        // Only advance the cached story list (and let the caller garbage collect the previous
        // set) once enough story bodies actually downloaded. Otherwise a spotty connection that
        // fetches the id list but fails the item downloads would evict a working offline set.
        if (storiesCached < Math.ceil(total * COMMIT_THRESHOLD)) {
            return false;
        }
        StoryListCache.put(this, ItemManager.TOP_FETCH_MODE, topStories, limit);
        StoryListCache.put(this, ItemManager.BEST_FETCH_MODE, bestStories, limit);
        return true;
    }

    @WorkerThread
    private boolean isCached(@NonNull HackerNewsClient.RestService service, @NonNull String id) {
        HackerNewsItem item = cachedItem(service, id);
        if (item == null) {
            return false;
        }
        return isReadabilityCached(item) && isArticleCached(item) &&
                areCommentsCached(service, item);
    }

    private boolean isReadabilityCached(@NonNull HackerNewsItem item) {
        return !Preferences.Offline.isReadabilityEnabled(this) ||
                !item.isStoryType() ||
                mLocalCache.getReadability(item.getId()) != null;
    }

    private boolean isArticleCached(@NonNull HackerNewsItem item) {
        return !Preferences.Offline.isArticleEnabled(this) ||
                !item.isStoryType() ||
                TextUtils.isEmpty(item.getUrl()) ||
                ArticleCache.contains(this, item.getUrl());
    }

    private boolean areCommentsCached(@NonNull HackerNewsClient.RestService service,
                                      @NonNull HackerNewsItem item) {
        if (!Preferences.Offline.isCommentsEnabled(this) || item.getKids() == null) {
            return true;
        }
        for (long kid : item.getKids()) {
            HackerNewsItem child = cachedItem(service, String.valueOf(kid));
            if (child == null || !areCommentsCached(service, child)) {
                return false;
            }
        }
        return true;
    }

    private void syncStory(@NonNull String id) {
        CountDownLatch latch = new CountDownLatch(1);
        final SyncDelegate[] syncDelegate = new SyncDelegate[1];
        new Handler(Looper.getMainLooper()).post(() -> {
            syncDelegate[0] = new SyncDelegate(this, mFactory, mReadabilityClient);
            syncDelegate[0].subscribe(token -> latch.countDown());
            syncDelegate[0].performSync(new SyncDelegate.JobBuilder(this, id)
                    .setNotificationEnabled(false)
                    .build());
        });
        try {
            // Cap per-story work so a spotty connection can't stall the job for hours.
            if (!latch.await(3, TimeUnit.MINUTES)) {
                stopSync(syncDelegate[0]);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopSync(syncDelegate[0]);
        }
    }

    private void stopSync(SyncDelegate syncDelegate) {
        if (syncDelegate != null) {
            syncDelegate.stopSync();
        }
    }

    private HackerNewsItem cachedItem(@NonNull HackerNewsClient.RestService service,
                                      @NonNull String id) {
        HackerNewsItem cachedItem = HackerNewsItemCache.get(this, id);
        if (cachedItem != null) {
            return cachedItem;
        }
        try {
            HackerNewsItem item = service.cachedItem(id).execute().body();
            HackerNewsItemCache.put(this, item);
            return item;
        } catch (IOException e) {
            return null;
        }
    }

    private void addStories(@NonNull Set<Integer> ids, int[] stories, int limit) {
        if (stories == null) {
            return;
        }
        for (int i = 0; i < stories.length && i < limit; i++) {
            ids.add(stories[i]);
        }
    }

    private int[] networkTopStories(HackerNewsClient.RestService service) {
        try {
            return service.networkTopStories().execute().body();
        } catch (IOException e) {
            return null;
        }
    }

    private int[] networkBestStories(HackerNewsClient.RestService service) {
        try {
            return service.networkBestStories().execute().body();
        } catch (IOException e) {
            return null;
        }
    }

    private void updateProgress(int progress, int max, boolean indeterminate) {
        if (!Preferences.Offline.isNotificationEnabled(this) || !canPostNotifications()) {
            return;
        }
        NotificationCompat.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                new NotificationCompat.Builder(this, DOWNLOADS_CHANNEL_ID) :
                new NotificationCompat.Builder(this);
        mNotificationManager.notify(NOTIFICATION_ID, builder
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.hot_cache_in_progress))
                .setContentText(indeterminate ?
                        getString(R.string.download_in_progress) :
                        getString(R.string.hot_cache_progress, progress, max))
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setProgress(max, progress, indeterminate)
                .build());
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED;
    }

    private void cancelProgress() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID);
        }
    }
}
