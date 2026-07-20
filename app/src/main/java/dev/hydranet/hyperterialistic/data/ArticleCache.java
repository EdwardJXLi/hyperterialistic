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

import dev.hydranet.hyperterialistic.widget.CacheableWebView;

/**
 * Whether an article page is available offline. Derived from the saved web archive itself
 * rather than a separate flag: a flag written at "progress 100" could mark pages as cached
 * whose archive never got written (or captured an error page), leaving the hot cache
 * convinced it was done while the reader showed a blank page.
 */
final class ArticleCache {

    private ArticleCache() { }

    static boolean contains(Context context, String url) {
        return CacheableWebView.hasValidArchive(context, url);
    }
}
