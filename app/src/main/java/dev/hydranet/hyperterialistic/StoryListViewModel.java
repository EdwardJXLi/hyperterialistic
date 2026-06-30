package dev.hydranet.hyperterialistic;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.core.util.Pair;

import dev.hydranet.hyperterialistic.data.Item;
import dev.hydranet.hyperterialistic.data.ItemManager;
import rx.Observable;
import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;

public class StoryListViewModel extends ViewModel {
    private ItemManager mItemManager;
    private Scheduler mIoThreadScheduler;
    private MutableLiveData<Pair<Item[], Item[]>> mItems; // first = last updated, second = current

    public void inject(ItemManager itemManager, Scheduler ioThreadScheduler) {
        mItemManager = itemManager;
        mIoThreadScheduler = ioThreadScheduler;
    }

    public LiveData<Pair<Item[], Item[]>> getStories(String filter, @ItemManager.CacheMode int cacheMode) {
        if (mItems == null) {
            mItems = new MutableLiveData<>();
            Observable.fromCallable(() -> mItemManager.getStories(filter, cacheMode))
                    .subscribeOn(mIoThreadScheduler)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(items -> setItems(items), throwable -> setItems(null));
        }
        return mItems;
    }

    public void refreshStories(String filter, @ItemManager.CacheMode int cacheMode) {
        // Note: do not bail when getValue() is null. If the initial load hung (e.g. a flaky
        // connection that never completed before call timeouts were in place), getValue() stays
        // null forever and a guarded refresh would no-op on every subsequent pull, leaving the
        // refresh spinner stuck until the app is force-restarted.
        if (mItems == null) {
            getStories(filter, cacheMode);
            return;
        }
        Observable.fromCallable(() -> mItemManager.getStories(filter, cacheMode))
                .subscribeOn(mIoThreadScheduler)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> setItems(items), throwable -> setItems(null));

    }

    void setItems(Item[] items) {
        mItems.setValue(Pair.create(mItems.getValue() != null ? mItems.getValue().second : null, items));
    }
}
