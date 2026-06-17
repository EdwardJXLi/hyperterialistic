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
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;

import java.util.Set;

final class HackerNewsItemCache {
    private static final String PREFERENCES_FILE = "hn_item_cache";
    private static final Gson GSON = new Gson();

    private HackerNewsItemCache() { }

    static HackerNewsItem get(Context context, String itemId) {
        if (TextUtils.isEmpty(itemId)) {
            return null;
        }
        String json = preferences(context).getString(itemId, null);
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        try {
            return GSON.fromJson(json, HackerNewsItem.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static void put(Context context, HackerNewsItem item) {
        if (item == null || TextUtils.isEmpty(item.getId())) {
            return;
        }
        preferences(context).edit()
                .putString(item.getId(), GSON.toJson(item))
                .apply();
    }

    static void clear(Context context) {
        preferences(context).edit().clear().apply();
    }

    static void retainOnly(Context context, Set<String> retainedItemIds) {
        SharedPreferences preferences = preferences(context);
        if (retainedItemIds == null || retainedItemIds.isEmpty()) {
            preferences.edit().clear().apply();
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        for (String itemId : preferences.getAll().keySet()) {
            if (!retainedItemIds.contains(itemId)) {
                editor.remove(itemId);
            }
        }
        editor.apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
    }
}
