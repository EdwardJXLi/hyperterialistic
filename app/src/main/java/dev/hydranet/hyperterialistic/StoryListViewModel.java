package dev.hydranet.hyperterialistic;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.core.util.Pair;

import java.util.concurrent.TimeUnit;

import dev.hydranet.hyperterialistic.data.Item;
import dev.hydranet.hyperterialistic.data.ItemManager;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class StoryListViewModel extends ViewModel {
    // A refresh is a gesture the user is watching, so it shouldn't hold the spinner for the whole
    // 30s call timeout when the link is dead. Give up well before that and settle on the cached
    // feed instead. Still long enough for a slow-but-working connection to answer.
    private static final int LOAD_TIMEOUT_SECONDS = 8;

    private ItemManager mItemManager;
    private Scheduler mIoThreadScheduler;
    private MutableLiveData<Pair<Item[], Item[]>> mItems; // first = last updated, second = current
    private Subscription mSubscription;
    private boolean mLastResultUpdated;

    public void inject(ItemManager itemManager, Scheduler ioThreadScheduler) {
        mItemManager = itemManager;
        mIoThreadScheduler = ioThreadScheduler;
    }

    public LiveData<Pair<Item[], Item[]>> getStories(String filter, @ItemManager.CacheMode int cacheMode) {
        if (mItems == null) {
            mItems = new MutableLiveData<>();
            load(filter, cacheMode);
        }
        return mItems;
    }

    public void refreshStories(String filter, @ItemManager.CacheMode int cacheMode) {
        // Don't bail on null getValue(): a hung initial load would otherwise no-op every refresh.
        if (mItems == null) {
            getStories(filter, cacheMode);
            return;
        }
        load(filter, cacheMode);
    }

    private void load(String filter, @ItemManager.CacheMode int cacheMode) {
        // Cancel any in-flight load so a rapid refresh can't race an older result over a newer one.
        if (mSubscription != null) {
            mSubscription.unsubscribe();
        }
        // getStories returns null when the fetch failed, so substituting the cache is done here
        // rather than down in the client: that's what keeps "this is fresh" separable from "this
        // is what we had", which the last-updated label depends on.
        Observable<Result> load = Observable
                .fromCallable(() -> mItemManager.getStories(filter, cacheMode))
                .flatMap(items -> items != null ?
                        // A MODE_CACHE load succeeds without ever asking the network, so it is
                        // never a fresh result no matter that it returned something.
                        Observable.just(new Result(items, cacheMode != ItemManager.MODE_CACHE)) :
                        cachedResult(filter).defaultIfEmpty(new Result(null, false)));
        if (cacheMode != ItemManager.MODE_CACHE) {
            // Falls back to the cached feed, or to null items - which leaves whatever is on screen
            // alone. Emitting either way is what stops the refresh spinner.
            load = load.timeout(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS,
                    cachedResult(filter).defaultIfEmpty(new Result(null, false)));
        }
        if (cacheMode == ItemManager.MODE_DEFAULT) {
            // A dead-but-connected link (the subway case) still looks online, so the network load
            // burns its full call timeout before falling back - half a minute of blank feed. Emit
            // what's already cached first and let the network result replace it when it lands.
            // Only for the automatic load: a pull-to-refresh asks for fresh data, and its list is
            // already on screen, so pushing the cached one back over it would just show older data.
            load = Observable.concat(cachedResult(filter), load);
        }
        mSubscription = load
                .subscribeOn(mIoThreadScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::setResult, throwable -> setResult(new Result(null, false)));
    }

    private Observable<Result> cachedResult(String filter) {
        return cachedStories(filter).map(items -> new Result(items, false));
    }

    // subscribeOn is set here rather than left to the caller because the timeout operator
    // subscribes to this from its own scheduler, and reading the caches is disk work.
    private Observable<Item[]> cachedStories(String filter) {
        return Observable.fromCallable(() -> mItemManager.getCachedStories(filter))
                .subscribeOn(mIoThreadScheduler)
                .filter(items -> items != null && items.length > 0)
                .onErrorResumeNext(Observable.empty());
    }

    public boolean isLoading() {
        return mSubscription != null && !mSubscription.isUnsubscribed();
    }

    /**
     * Whether the result currently held actually came off the network. False when it was served
     * from a cache because the fetch failed or timed out - the feed is readable either way, but
     * calling it freshly updated would be a lie.
     */
    public boolean isLastResultUpdated() {
        return mLastResultUpdated;
    }

    private void setResult(Result result) {
        // Safe to pair with the value below because setValue dispatches to observers synchronously
        // on this thread, so the flag can't be overwritten before they read it.
        mLastResultUpdated = result.updated;
        setItems(result.items);
    }

    void setItems(Item[] items) {
        mItems.setValue(Pair.create(mItems.getValue() != null ? mItems.getValue().second : null, items));
    }

    @Override
    protected void onCleared() {
        if (mSubscription != null) {
            mSubscription.unsubscribe();
            mSubscription = null;
        }
        super.onCleared();
    }

    private static final class Result {
        final Item[] items;
        final boolean updated;

        Result(Item[] items, boolean updated) {
            this.items = items;
            this.updated = updated;
        }
    }
}
