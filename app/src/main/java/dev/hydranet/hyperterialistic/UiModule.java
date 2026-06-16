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

package dev.hydranet.hyperterialistic;

import android.annotation.SuppressLint;
import android.content.Context;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dev.hydranet.hyperterialistic.appwidget.WidgetConfigActivity;
import dev.hydranet.hyperterialistic.widget.FavoriteRecyclerViewAdapter;
import dev.hydranet.hyperterialistic.widget.MultiPageItemRecyclerViewAdapter;
import dev.hydranet.hyperterialistic.widget.PopupMenu;
import dev.hydranet.hyperterialistic.widget.SinglePageItemRecyclerViewAdapter;
import dev.hydranet.hyperterialistic.widget.StoryRecyclerViewAdapter;
import dev.hydranet.hyperterialistic.widget.SubmissionRecyclerViewAdapter;
import dev.hydranet.hyperterialistic.widget.ThreadPreviewRecyclerViewAdapter;

@Module(
        injects = {
                AboutActivity.class,
                AskActivity.class,
                BestActivity.class,
                ComposeActivity.class,
                FavoriteActivity.class,
                FeedbackActivity.class,
                ItemActivity.class,
                JobsActivity.class,
                ListActivity.class,
                LoginActivity.class,
                NewActivity.class,
                OfflineWebActivity.class,
                PopularActivity.class,
                ReleaseNotesActivity.class,
                SearchActivity.class,
                SettingsActivity.class,
                ShowActivity.class,
                SubmitActivity.class,
                ThreadPreviewActivity.class,
                UserActivity.class,
                WidgetConfigActivity.class,
                FavoriteFragment.class,
                ItemFragment.class,
                ListFragment.class,
                WebFragment.class,
                FavoriteRecyclerViewAdapter.class,
                SinglePageItemRecyclerViewAdapter.class,
                StoryRecyclerViewAdapter.class,
                SubmissionRecyclerViewAdapter.class,
                MultiPageItemRecyclerViewAdapter.class,
                ThreadPreviewRecyclerViewAdapter.class
        },
        library = true,
        complete = false
)
class UiModule {
    @Provides
    public PopupMenu providePopupMenu() {
        return new PopupMenu.Impl();
    }

    @Provides @Singleton
    public CustomTabsDelegate provideCustomTabsDelegate() {
        return new CustomTabsDelegate();
    }

    @Provides @Singleton
    public KeyDelegate provideKeyDelegate() {
        return new KeyDelegate();
    }

    @Provides @Singleton
    public ActionViewResolver provideActionViewResolver() {
        return new ActionViewResolver();
    }

    @Provides
    public AlertDialogBuilder provideAlertDialogBuilder() {
        return new AlertDialogBuilder.Impl();
    }

    @SuppressLint("Recycle")
    @Provides @Singleton
    public ResourcesProvider provideResourcesProvider(Context context) {
        return resId -> context.getResources().obtainTypedArray(resId);
    }
}
