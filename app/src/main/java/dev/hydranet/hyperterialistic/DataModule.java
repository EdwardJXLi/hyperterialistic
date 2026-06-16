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

import androidx.sqlite.db.SupportSQLiteOpenHelper;
import android.content.Context;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dev.hydranet.hyperterialistic.accounts.UserServices;
import dev.hydranet.hyperterialistic.accounts.UserServicesClient;
import dev.hydranet.hyperterialistic.data.AlgoliaClient;
import dev.hydranet.hyperterialistic.data.AlgoliaPopularClient;
import dev.hydranet.hyperterialistic.data.FeedbackClient;
import dev.hydranet.hyperterialistic.data.FavoriteManager;
import dev.hydranet.hyperterialistic.data.HackerNewsClient;
import dev.hydranet.hyperterialistic.data.ItemManager;
import dev.hydranet.hyperterialistic.data.LocalCache;
import dev.hydranet.hyperterialistic.data.MaterialisticDatabase;
import dev.hydranet.hyperterialistic.data.ReadabilityClient;
import dev.hydranet.hyperterialistic.data.SessionManager;
import dev.hydranet.hyperterialistic.data.SyncScheduler;
import dev.hydranet.hyperterialistic.data.UserManager;
import dev.hydranet.hyperterialistic.data.android.Cache;
import okhttp3.Call;
import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

import static dev.hydranet.hyperterialistic.ActivityModule.ALGOLIA;
import static dev.hydranet.hyperterialistic.ActivityModule.HN;
import static dev.hydranet.hyperterialistic.ActivityModule.POPULAR;

@Module(library = true, complete = false, includes = NetworkModule.class)
public class DataModule {
    public static final String MAIN_THREAD = "main";
    public static final String IO_THREAD = "io";

    @Provides @Singleton @Named(HN)
    public ItemManager provideHackerNewsClient(HackerNewsClient client) {
        return client;
    }

    @Provides @Singleton @Named(ALGOLIA)
    public ItemManager provideAlgoliaClient(AlgoliaClient client) {
        return client;
    }

    @Provides @Singleton @Named(POPULAR)
    public ItemManager provideAlgoliaPopularClient(AlgoliaPopularClient client) {
        return client;
    }

    @Provides @Singleton
    public UserManager provideUserManager(HackerNewsClient client) {
        return client;
    }

    @Provides @Singleton
    public FeedbackClient provideFeedbackClient(FeedbackClient.Impl client) {
        return client;
    }

    @Provides @Singleton
    public ReadabilityClient provideReadabilityClient(ReadabilityClient.Impl client) {
        return client;
    }

    @Provides @Singleton
    public UserServices provideUserServices(Call.Factory callFactory,
                                            @Named(IO_THREAD) Scheduler ioScheduler) {
        return new UserServicesClient(callFactory, ioScheduler);
    }

    @Provides @Singleton @Named(IO_THREAD)
    public Scheduler provideIoScheduler() {
        return Schedulers.io();
    }

    @Provides @Singleton @Named(MAIN_THREAD)
    public Scheduler provideMainThreadScheduler() {
        return AndroidSchedulers.mainThread();
    }

    @Provides @Singleton
    public SyncScheduler provideSyncScheduler() {
        return new SyncScheduler();
    }

    @Provides @Singleton
    public Cache provideCache(MaterialisticDatabase database,
                              MaterialisticDatabase.SavedStoriesDao savedStoriesDao,
                              MaterialisticDatabase.ReadStoriesDao readStoriesDao,
                              MaterialisticDatabase.ReadableDao readableDao,
                              @Named(MAIN_THREAD) Scheduler mainScheduler) {
        return new Cache(database, savedStoriesDao, readStoriesDao, readableDao, mainScheduler);
    }

    @Provides @Singleton
    public LocalCache provideLocalCache(Cache cache) {
        return cache;
    }

    @Provides @Singleton
    public FavoriteManager provideFavoriteManager(LocalCache cache,
                                                  @Named(IO_THREAD) Scheduler ioScheduler,
                                                  MaterialisticDatabase.SavedStoriesDao savedStoriesDao) {
        return new FavoriteManager(cache, ioScheduler, savedStoriesDao);
    }

    @Provides @Singleton
    public SessionManager provideSessionManager(@Named(IO_THREAD) Scheduler ioScheduler,
                                                LocalCache cache) {
        return new SessionManager(ioScheduler, cache);
    }

    @Provides @Singleton
    public MaterialisticDatabase provideDatabase(Context context) {
        return MaterialisticDatabase.getInstance(context);
    }

    @Provides
    public MaterialisticDatabase.SavedStoriesDao provideSavedStoriesDao(MaterialisticDatabase database) {
        return database.getSavedStoriesDao();
    }

    @Provides
    public MaterialisticDatabase.ReadStoriesDao provideReadStoriesDao(MaterialisticDatabase database) {
        return database.getReadStoriesDao();
    }

    @Provides
    public MaterialisticDatabase.ReadableDao provideReadableDao(MaterialisticDatabase database) {
        return database.getReadableDao();
    }

    @Provides
    public SupportSQLiteOpenHelper provideOpenHelper(MaterialisticDatabase database) {
        return database.getOpenHelper();
    }
}
