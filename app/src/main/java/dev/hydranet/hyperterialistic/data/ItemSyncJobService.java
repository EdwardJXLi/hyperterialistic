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
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import android.text.TextUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;

import dev.hydranet.hyperterialistic.ActivityModule;
import dev.hydranet.hyperterialistic.Injectable;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ItemSyncJobService extends JobService {
    @Inject RestServiceFactory mFactory;
    @Inject ReadabilityClient mReadabilityClient;
    private final Map<String, SyncDelegate> mSyncDelegates = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> mSyncTasks = new ConcurrentHashMap<>();
    // JobService callbacks run on the main thread; sync work (blocking cache reads and
    // recursive comment-tree traversal) must be moved off it to avoid freezing the UI / ANRs.
    private final ExecutorService mExecutor = Executors.newCachedThreadPool();

    @Override
    public void onCreate() {
        super.onCreate();
        ((Injectable) getApplication())
                .getApplicationGraph()
                .plus(new ActivityModule(this))
                .inject(this);
    }

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        String jobId = String.valueOf(jobParameters.getJobId());
        SyncDelegate syncDelegate = createSyncDelegate();
        mSyncDelegates.put(jobId, syncDelegate);
        syncDelegate.subscribe(token -> {
            if (TextUtils.equals(jobId, token)) {
                jobFinished(jobParameters, false);
                mSyncDelegates.remove(jobId);
                mSyncTasks.remove(jobId);
            }
        });
        mSyncTasks.put(jobId, mExecutor.submit(() ->
                syncDelegate.performSync(new SyncDelegate.Job(jobParameters.getExtras()))));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        String key = String.valueOf(jobParameters.getJobId());
        Future<?> task = mSyncTasks.remove(key);
        if (task != null) {
            task.cancel(true);
        }
        SyncDelegate syncDelegate = mSyncDelegates.remove(key);
        if (syncDelegate != null) {
            syncDelegate.stopSync();
        }
        return true;
    }

    @Override
    public void onDestroy() {
        mExecutor.shutdownNow();
        super.onDestroy();
    }

    @VisibleForTesting
    @NonNull
    SyncDelegate createSyncDelegate() {
        return new SyncDelegate(this, mFactory, mReadabilityClient);
    }
}
