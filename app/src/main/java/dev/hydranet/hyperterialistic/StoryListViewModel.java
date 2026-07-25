package dev.hydranet.hyperterialistic;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.core.util.Pair;

import dev.hydranet.hyperterialistic.data.Item;
import dev.hydranet.hyperterialistic.data.ItemManager;
import rx.Observable;
import rx.Scheduler;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class StoryListViewModel extends ViewModel {
    private ItemManager mItemManager;
    private Scheduler mIoThreadScheduler;
    private MutableLiveData<Pair<Item[], Item[]>> mItems; // first = last updated, second = current
    private Subscription mSubscription;

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
        Observable<Item[]> load = Observable.fromCallable(
                () -> mItemManager.getStories(filter, cacheMode));
        if (cacheMode == ItemManager.MODE_DEFAULT) {
            // A dead-but-connected link (the subway case) still looks online, so the network load
            // burns its full call timeout before falling back - half a minute of blank feed. Emit
            // what's already cached first and let the network result replace it when it lands.
            // Only for the automatic load: a pull-to-refresh asks for fresh data, and its list is
            // already on screen, so pushing the cached one back over it would just show older data.
            load = Observable.concat(cachedStories(filter), load);
        }
        mSubscription = load
                .subscribeOn(mIoThreadScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::setItems, throwable -> setItems(null));
    }

    private Observable<Item[]> cachedStories(String filter) {
        return Observable.fromCallable(() -> mItemManager.getCachedStories(filter))
                .filter(items -> items != null && items.length > 0)
                .onErrorResumeNext(Observable.empty());
    }

    public boolean isLoading() {
        return mSubscription != null && !mSubscription.isUnsubscribed();
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
}
