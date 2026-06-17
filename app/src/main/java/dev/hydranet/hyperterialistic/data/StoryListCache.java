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

import androidx.annotation.Nullable;

class StoryListCache {
    private static final String PREFERENCES_FILE = "_storylistcache";

    static void put(Context context, @ItemManager.FetchMode String filter, int[] ids, int limit) {
        if (ids == null || ids.length == 0) {
            return;
        }
        int count = Math.min(ids.length, limit);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(ids[i]);
        }
        getPreferences(context).edit()
                .putString(filter, builder.toString())
                .apply();
    }

    @Nullable
    static int[] get(Context context, @ItemManager.FetchMode String filter) {
        String value = getPreferences(context).getString(filter, null);
        if (TextUtils.isEmpty(value)) {
            return null;
        }
        String[] parts = value.split(",");
        int[] ids = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                ids[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return ids;
    }

    static void clear(Context context) {
        getPreferences(context).edit().clear().apply();
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(
                context.getPackageName() + PREFERENCES_FILE, Context.MODE_PRIVATE);
    }
}
