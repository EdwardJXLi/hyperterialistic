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

import java.util.Set;

final class ArticleCache {
    private static final String PREFERENCES_FILE = "article_cache";

    private ArticleCache() { }

    static boolean contains(Context context, String url) {
        return !TextUtils.isEmpty(url) &&
                preferences(context).getBoolean(url, false);
    }

    static void put(Context context, String url) {
        if (!TextUtils.isEmpty(url)) {
            preferences(context).edit().putBoolean(url, true).apply();
        }
    }

    static void clear(Context context) {
        preferences(context).edit().clear().apply();
    }

    static void retainOnly(Context context, Set<String> retainedUrls) {
        SharedPreferences preferences = preferences(context);
        if (retainedUrls == null || retainedUrls.isEmpty()) {
            preferences.edit().clear().apply();
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        for (String url : preferences.getAll().keySet()) {
            if (!retainedUrls.contains(url)) {
                editor.remove(url);
            }
        }
        editor.apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE);
    }
}
