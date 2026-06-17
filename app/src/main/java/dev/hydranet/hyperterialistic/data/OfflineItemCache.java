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
import android.text.TextUtils;

import java.io.IOException;

import javax.inject.Inject;

public class OfflineItemCache {
    public static final int CACHE_NONE = 0;
    public static final int CACHE_PARTIAL = 1;
    public static final int CACHE_FULL = 2;

    private final Context mContext;
    private final HackerNewsClient.RestService mHnRestService;

    @Inject
    public OfflineItemCache(Context context, RestServiceFactory factory) {
        mContext = context.getApplicationContext();
        mHnRestService = factory.create(HackerNewsClient.BASE_API_URL,
                HackerNewsClient.RestService.class);
    }

    public boolean isItemCached(String itemId) {
        return cachedItem(itemId) != null;
    }

    public boolean isStoryCached(String itemId) {
        return getStoryCacheStatus(itemId) == CACHE_FULL;
    }

    public int getStoryCacheStatus(String itemId) {
        HackerNewsItem item = cachedItem(itemId);
        if (item == null) {
            return CACHE_NONE;
        }
        return areCommentsCached(item) ? CACHE_FULL : CACHE_PARTIAL;
    }

    private boolean areCommentsCached(HackerNewsItem item) {
        if (item.getKids() == null) {
            return true;
        }
        for (long kid : item.getKids()) {
            HackerNewsItem child = cachedItem(String.valueOf(kid));
            if (child == null || !areCommentsCached(child)) {
                return false;
            }
        }
        return true;
    }

    private HackerNewsItem cachedItem(String itemId) {
        if (TextUtils.isEmpty(itemId)) {
            return null;
        }
        HackerNewsItem cachedItem = HackerNewsItemCache.get(mContext, itemId);
        if (cachedItem != null) {
            return cachedItem;
        }
        try {
            return mHnRestService.cachedItem(itemId).execute().body();
        } catch (IOException e) {
            return null;
        }
    }
}
