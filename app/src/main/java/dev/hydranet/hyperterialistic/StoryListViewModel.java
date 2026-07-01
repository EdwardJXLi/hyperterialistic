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
        mSubscription = Observable.fromCallable(() -> mItemManager.getStories(filter, cacheMode))
                .subscribeOn(mIoThreadScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::setItems, throwable -> setItems(null));
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
