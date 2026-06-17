/*
 * Copyright (c) 2017 Ha Duy Trung
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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.text.format.DateUtils;

import androidx.annotation.RequiresApi;

import dev.hydranet.hyperterialistic.Preferences;

public class SyncScheduler {
    private static final int HOT_CACHE_JOB_ID = Integer.MAX_VALUE;
    private static final int HOT_CACHE_NOW_JOB_ID = Integer.MAX_VALUE - 1;

    public void scheduleSync(Context context, String itemId) {
        SyncDelegate.scheduleSync(context, new SyncDelegate.JobBuilder(context, itemId).build());
    }

    public static void scheduleHotCache(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (!Preferences.Offline.isHotCacheEnabled(context)) {
            scheduler.cancel(HOT_CACHE_JOB_ID);
            scheduler.cancel(HOT_CACHE_NOW_JOB_ID);
            return;
        }
        scheduler.schedule(createHotCacheJob(context));
        scheduler.schedule(createHotCacheJobNow(context));
    }

    public static boolean scheduleHotCacheNow(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ||
                !Preferences.Offline.isHotCacheEnabled(context)) {
            return false;
        }
        ((JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE))
                .schedule(createHotCacheJobNow(context));
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static JobInfo createHotCacheJob(Context context) {
        long intervalMillis = Preferences.Offline.getHotCacheIntervalHours(context) *
                DateUtils.HOUR_IN_MILLIS;
        JobInfo.Builder builder = new JobInfo.Builder(HOT_CACHE_JOB_ID,
                new ComponentName(context.getPackageName(), HotCacheJobService.class.getName()))
                .setPeriodic(intervalMillis)
                .setRequiredNetworkType(Preferences.Offline.isWifiOnly(context) ?
                        JobInfo.NETWORK_TYPE_UNMETERED :
                        JobInfo.NETWORK_TYPE_ANY);
        return builder.build();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private static JobInfo createHotCacheJobNow(Context context) {
        JobInfo.Builder builder = new JobInfo.Builder(HOT_CACHE_NOW_JOB_ID,
                new ComponentName(context.getPackageName(), HotCacheJobService.class.getName()))
                .setRequiredNetworkType(Preferences.Offline.isWifiOnly(context) ?
                        JobInfo.NETWORK_TYPE_UNMETERED :
                        JobInfo.NETWORK_TYPE_ANY);
        if (Preferences.Offline.currentConnectionEnabled(context)) {
            builder.setOverrideDeadline(0);
        }
        return builder.build();
    }
}
