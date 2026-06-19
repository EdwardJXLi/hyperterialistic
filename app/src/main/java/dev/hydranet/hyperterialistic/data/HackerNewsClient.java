/*
 * Copyright (c) 2015 Ha Duy Trung
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

import androidx.annotation.NonNull;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;

import dev.hydranet.hyperterialistic.DataModule;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Path;
import rx.Observable;
import rx.Scheduler;

/**
 * Client to retrieve Hacker News content asynchronously
 */
public class HackerNewsClient implements ItemManager, UserManager {
    public static final String HOST = "hacker-news.firebaseio.com";
    public static final String BASE_WEB_URL = "https://news.ycombinator.com";
    public static final String WEB_ITEM_PATH = BASE_WEB_URL + "/item?id=%s";
    static final String BASE_API_URL = "https://" + HOST + "/v0/";
    @Inject @Named(DataModule.IO_THREAD) Scheduler mIoScheduler;
    @Inject @Named(DataModule.MAIN_THREAD) Scheduler mMainThreadScheduler;
    private final RestService mRestService;
    private final SessionManager mSessionManager;
    private final FavoriteManager mFavoriteManager;
    private final Context mContext;

    @Inject
    public HackerNewsClient(Context context,
                            RestServiceFactory factory,
                            SessionManager sessionManager,
                            FavoriteManager favoriteManager) {
        mContext = context.getApplicationContext();
        mRestService = factory.rxEnabled(true).create(BASE_API_URL, RestService.class);
        mSessionManager = sessionManager;
        mFavoriteManager = favoriteManager;
    }

    @Override
    public void getStories(@FetchMode String filter, @CacheMode int cacheMode,
                           final ResponseListener<Item[]> listener) {
        if (listener == null) {
            return;
        }
        Observable.defer(() -> getStoriesObservable(filter, cacheMode))
                .subscribeOn(mIoScheduler)
                .observeOn(mMainThreadScheduler)
                .subscribe(items -> listener.onResponse(items != null ?
                                items : getCachedStories(filter)),
                        t -> {
                            Item[] cached = getCachedStories(filter);
                            if (cached != null) {
                                listener.onResponse(cached);
                            } else {
                                listener.onError(t != null ? t.getMessage() : "");
                            }
                        });
    }

    @Override
    public void getItem(final String itemId, @CacheMode int cacheMode, ResponseListener<Item> listener) {
        if (listener == null) {
            return;
        }
        Observable<HackerNewsItem> itemObservable;
        switch (cacheMode) {
            case MODE_DEFAULT:
            default:
                itemObservable = mRestService.itemRx(itemId)
                        .doOnNext(item -> HackerNewsItemCache.put(mContext, item));
                break;
            case MODE_NETWORK:
                itemObservable = mRestService.networkItemRx(itemId)
                        .doOnNext(item -> HackerNewsItemCache.put(mContext, item));
                break;
            case MODE_CACHE:
                itemObservable = Observable.fromCallable(() -> HackerNewsItemCache.get(mContext, itemId))
                        .flatMap(item -> item != null ?
                                Observable.just(item) :
                                mRestService.cachedItemRx(itemId)
                                        .doOnNext(cachedItem ->
                                                HackerNewsItemCache.put(mContext, cachedItem)));
                break;
        }
        Observable.defer(() -> Observable.zip(
                mSessionManager.isViewed(itemId),
                mFavoriteManager.check(itemId),
                itemObservable,
                (isViewed, favorite, hackerNewsItem) -> {
                    if (hackerNewsItem != null) {
                        hackerNewsItem.preload();
                        hackerNewsItem.setIsViewed(isViewed);
                        hackerNewsItem.setFavorite(favorite);
                    }
                    return hackerNewsItem;
                }))
                .subscribeOn(mIoScheduler)
                .observeOn(mMainThreadScheduler)
                .subscribe(listener::onResponse,
                        t -> listener.onError(t != null ? t.getMessage() : ""));

    }

    @Override
    public Item[] getStories(String filter, @CacheMode int cacheMode) {
        try {
            int[] ids = getStoriesCall(filter, cacheMode).execute().body();
            if (ids == null) {
                ids = StoryListCache.get(mContext, normalizeFilter(filter));
            }
            Item[] items = toItems(ids);
            return items != null ? items : new Item[0];
        } catch (IOException e) {
            Item[] cached = getCachedStories(filter);
            return cached != null ? cached : new Item[0];
        }
    }

    @Override
    public Item getItem(String itemId, @CacheMode int cacheMode) {
        Call<HackerNewsItem> call;
        switch (cacheMode) {
            case MODE_DEFAULT:
            default:
                call = mRestService.item(itemId);
                break;
            case MODE_CACHE:
                HackerNewsItem cachedItem = HackerNewsItemCache.get(mContext, itemId);
                if (cachedItem != null) {
                    return cachedItem;
                }
                call = mRestService.cachedItem(itemId);
                break;
            case MODE_NETWORK:
                call = mRestService.networkItem(itemId);
                break;
        }
        try {
            HackerNewsItem item = call.execute().body();
            HackerNewsItemCache.put(mContext, item);
            return item;
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void getUser(String username, final ResponseListener<User> listener) {
        if (listener == null) {
            return;
        }
        mRestService.userRx(username)
                .map(userItem -> {
                    if (userItem != null) {
                        userItem.setSubmittedItems(toItems(userItem.getSubmitted()));
                    }
                    return userItem;
                })
                .subscribeOn(mIoScheduler)
                .observeOn(mMainThreadScheduler)
                .subscribe(listener::onResponse,
                        t -> listener.onError(t != null ? t.getMessage() : ""));
    }

    @NonNull
    private Observable<Item[]> getStoriesObservable(@FetchMode String filter, @CacheMode int cacheMode) {
        Observable<int[]> observable;
        switch (filter) {
            case NEW_FETCH_MODE:
                observable = cacheMode == MODE_NETWORK ? mRestService.networkNewStoriesRx() :
                        cacheMode == MODE_CACHE ? mRestService.cachedNewStoriesRx() :
                                mRestService.newStoriesRx();
                break;
            case SHOW_FETCH_MODE:
                observable = cacheMode == MODE_NETWORK ? mRestService.networkShowStoriesRx() :
                        cacheMode == MODE_CACHE ? mRestService.cachedShowStoriesRx() :
                                mRestService.showStoriesRx();
                break;
            case ASK_FETCH_MODE:
                observable = cacheMode == MODE_NETWORK ? mRestService.networkAskStoriesRx() :
                        cacheMode == MODE_CACHE ? mRestService.cachedAskStoriesRx() :
                                mRestService.askStoriesRx();
                break;
            case JOBS_FETCH_MODE:
                observable = cacheMode == MODE_NETWORK ? mRestService.networkJobStoriesRx() :
                        cacheMode == MODE_CACHE ? mRestService.cachedJobStoriesRx() :
                                mRestService.jobStoriesRx();
                break;
            case BEST_FETCH_MODE:
                observable = cacheMode == MODE_NETWORK ? mRestService.networkBestStoriesRx() :
                        cacheMode == MODE_CACHE ? mRestService.cachedBestStoriesRx() :
                                mRestService.bestStoriesRx();
                break;
            default:
                observable = cacheMode == MODE_NETWORK ? mRestService.networkTopStoriesRx() :
                        cacheMode == MODE_CACHE ? mRestService.cachedTopStoriesRx() :
                                mRestService.topStoriesRx();
                break;
        }
        return observable.map(this::toItems);
    }

    @NonNull
    private String normalizeFilter(String filter) {
        return filter == null ? NEW_FETCH_MODE : filter;
    }

    private Item[] getCachedStories(String filter) {
        return toItems(StoryListCache.get(mContext, normalizeFilter(filter)));
    }

    @NonNull
    private Call<int[]> getStoriesCall(@FetchMode String filter, @CacheMode int cacheMode) {
        Call<int[]> call;
        if (filter == null) {
            // for legacy 'new stories' widgets
            return cacheMode == MODE_NETWORK ? mRestService.networkNewStories() :
                    cacheMode == MODE_CACHE ? mRestService.cachedNewStories() :
                            mRestService.newStories();
        }
        switch (filter) {
            case NEW_FETCH_MODE:
                call = cacheMode == MODE_NETWORK ? mRestService.networkNewStories() :
                        cacheMode == MODE_CACHE ? mRestService.cachedNewStories() :
                                mRestService.newStories();
                break;
            case SHOW_FETCH_MODE:
                call = cacheMode == MODE_NETWORK ? mRestService.networkShowStories() :
                        cacheMode == MODE_CACHE ? mRestService.cachedShowStories() :
                                mRestService.showStories();
                break;
            case ASK_FETCH_MODE:
                call = cacheMode == MODE_NETWORK ? mRestService.networkAskStories() :
                        cacheMode == MODE_CACHE ? mRestService.cachedAskStories() :
                                mRestService.askStories();
                break;
            case JOBS_FETCH_MODE:
                call = cacheMode == MODE_NETWORK ? mRestService.networkJobStories() :
                        cacheMode == MODE_CACHE ? mRestService.cachedJobStories() :
                                mRestService.jobStories();
                break;
            case BEST_FETCH_MODE:
                call = cacheMode == MODE_NETWORK ? mRestService.networkBestStories() :
                        cacheMode == MODE_CACHE ? mRestService.cachedBestStories() :
                                mRestService.bestStories();
                break;
            default:
                call = cacheMode == MODE_NETWORK ? mRestService.networkTopStories() :
                        cacheMode == MODE_CACHE ? mRestService.cachedTopStories() :
                                mRestService.topStories();
                break;
        }
        return call;
    }

    private HackerNewsItem[] toItems(int[] ids) {
        if (ids == null) {
            return null;
        }
        HackerNewsItem[] items = new HackerNewsItem[ids.length];
        for (int i = 0; i < items.length; i++) {
            HackerNewsItem item = new HackerNewsItem(ids[i]);
            item.rank = i + 1;
            items[i] = item;
        }
        return items;
    }

    interface RestService {
        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("topstories.json")
        Observable<int[]> topStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("newstories.json")
        Observable<int[]> newStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("showstories.json")
        Observable<int[]> showStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("askstories.json")
        Observable<int[]> askStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("jobstories.json")
        Observable<int[]> jobStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("beststories.json")
        Observable<int[]> bestStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("topstories.json")
        Observable<int[]> networkTopStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("newstories.json")
        Observable<int[]> networkNewStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("showstories.json")
        Observable<int[]> networkShowStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("askstories.json")
        Observable<int[]> networkAskStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("jobstories.json")
        Observable<int[]> networkJobStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("beststories.json")
        Observable<int[]> networkBestStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("topstories.json")
        Observable<int[]> cachedTopStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("newstories.json")
        Observable<int[]> cachedNewStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("showstories.json")
        Observable<int[]> cachedShowStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("askstories.json")
        Observable<int[]> cachedAskStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("jobstories.json")
        Observable<int[]> cachedJobStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("beststories.json")
        Observable<int[]> cachedBestStoriesRx();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("item/{itemId}.json")
        Observable<HackerNewsItem> itemRx(@Path("itemId") String itemId);

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("item/{itemId}.json")
        Observable<HackerNewsItem> networkItemRx(@Path("itemId") String itemId);

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("item/{itemId}.json")
        Observable<HackerNewsItem> cachedItemRx(@Path("itemId") String itemId);

        @GET("user/{userId}.json")
        Observable<UserItem> userRx(@Path("userId") String userId);

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("topstories.json")
        Call<int[]> topStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("newstories.json")
        Call<int[]> newStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("showstories.json")
        Call<int[]> showStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("askstories.json")
        Call<int[]> askStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("jobstories.json")
        Call<int[]> jobStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("beststories.json")
        Call<int[]> bestStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("topstories.json")
        Call<int[]> networkTopStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("newstories.json")
        Call<int[]> networkNewStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("showstories.json")
        Call<int[]> networkShowStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("askstories.json")
        Call<int[]> networkAskStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("jobstories.json")
        Call<int[]> networkJobStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("beststories.json")
        Call<int[]> networkBestStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("topstories.json")
        Call<int[]> cachedTopStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("newstories.json")
        Call<int[]> cachedNewStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("showstories.json")
        Call<int[]> cachedShowStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("askstories.json")
        Call<int[]> cachedAskStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("jobstories.json")
        Call<int[]> cachedJobStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("beststories.json")
        Call<int[]> cachedBestStories();

        @Headers(RestServiceFactory.CACHE_CONTROL_MAX_AGE_30M)
        @GET("item/{itemId}.json")
        Call<HackerNewsItem> item(@Path("itemId") String itemId);

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_NETWORK)
        @GET("item/{itemId}.json")
        Call<HackerNewsItem> networkItem(@Path("itemId") String itemId);

        @Headers(RestServiceFactory.CACHE_CONTROL_FORCE_CACHE)
        @GET("item/{itemId}.json")
        Call<HackerNewsItem> cachedItem(@Path("itemId") String itemId);

        @GET("user/{userId}.json")
        Call<UserItem> user(@Path("userId") String userId);
    }
}
