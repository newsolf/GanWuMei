package com.dimon.ganwumei.ui.newsfeed.fragment;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dimon.ganwumei.R;
import com.dimon.ganwumei.database.entities.Item;
import com.dimon.ganwumei.database.entities.News;
import com.dimon.ganwumei.network.HttpMethods;
import com.dimon.ganwumei.network.RestAPI;
import com.dimon.ganwumei.ui.base.BaseFragment;
import com.dimon.ganwumei.ui.newsfeed.adapter.AndroidListAdapter;
import com.dimon.ganwumei.util.GanWuDataToItemsMapper;
import com.dimon.ganwumei.widget.MultiSwipeRefreshLayout;
import com.socks.library.KLog;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import io.realm.Realm;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Created by Dimon on 2016/4/20.
 */
public class GanWuListFragment extends BaseFragment {

    @Bind(R.id.recyclerView)
    RecyclerView mRecyclerView;
    @Bind(R.id.swipe_refresh_layout)
    MultiSwipeRefreshLayout mSwipeRefreshLayout;
    private Realm mRealm;
    private View view;
    private List<Item> mNewsList;
    private static final String FRAGMENT_INDEX = "fragment_index";
    private int mGanWuIndex = -1;
    private int mPage = 1;
    private LinearLayoutManager linearLayoutManager;
    protected Subscription subscription;
    private static RestAPI restAPI;
    private AndroidListAdapter mAndroidListAdapter;
    private boolean mIsRequestDataRefresh = false;

    // 标志位，标志已经初始化完成
    private boolean isPrepared;

    // 是否已被加载过一次，第二次就不再去请求数据了
    private boolean mHasLoadedOnce;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (view == null) {
            view = inflater.inflate(R.layout.fragment_recyclerview, container, false);
            //获取索引值
            Bundle bundle = getArguments();
            if (bundle != null) {
                mGanWuIndex = bundle.getInt(FRAGMENT_INDEX);
            }
        }
        ButterKnife.bind(this, view);
        mRealm = Realm.getDefaultInstance();
        mNewsList = new ArrayList<>();

        //因为共用一个Fragment视图，所以当前这个视图已被加载到Activity中，必须先清除后再加入Activity
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) {
            parent.removeView(view);
        }
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        trySetupSwipeRefresh();
        new Handler().postDelayed(() -> setRequestDataRefresh(true), 358);
        loadData(false);
        KLog.a(mRecyclerView);
        linearLayoutManager = new LinearLayoutManager(context());
        mRecyclerView.setLayoutManager(linearLayoutManager);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mAndroidListAdapter = new AndroidListAdapter(mNewsList, context());
        mRecyclerView.setAdapter(mAndroidListAdapter);
    }

    private void loadData(boolean clean) {
        subscription = mRealm
                .where(News.class)
                .findAllSortedAsync("publishedAt")
                .asObservable()
                .filter(newses -> newses.isLoaded())
                .flatMap(newses1 ->
                        HttpMethods.getInstance().getGankService().getGanWuData("2016", "04", "20")
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread()))
                .map(GanWuDataToItemsMapper.getInstance())
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(newsess -> {
                    if (clean) mNewsList.clear();
                    mNewsList.addAll(newsess);
                    mAndroidListAdapter.notifyDataSetChanged();
                    setRequestDataRefresh(false);
                }, throwable -> loadError(throwable));
        addSubscription(subscription);
    }

    private void loadError(Throwable throwable) {
        throwable.printStackTrace();
        Snackbar.make(mRecyclerView, R.string.snap_load_fail,
                Snackbar.LENGTH_LONG).setAction(R.string.retry, v -> {
            requestDataRefresh();
        }).show();
    }

    void trySetupSwipeRefresh() {
        if (mSwipeRefreshLayout != null) {
            mSwipeRefreshLayout.setColorSchemeResources(R.color.refresh_progress_3,
                    R.color.refresh_progress_2, R.color.refresh_progress_1);
            // do not use lambda!!
            mSwipeRefreshLayout.setOnRefreshListener(
                    new SwipeRefreshLayout.OnRefreshListener() {
                        @Override
                        public void onRefresh() {
                            requestDataRefresh();
                        }
                    });
        }
    }

    public void requestDataRefresh() {
        mIsRequestDataRefresh = true;
        mPage = 1;
    }

    public void setRequestDataRefresh(boolean requestDataRefresh) {
        if (mSwipeRefreshLayout == null) {
            return;
        }
        if (!requestDataRefresh) {
            mIsRequestDataRefresh = false;
            // 防止刷新消失太快，让子弹飞一会儿.
            mSwipeRefreshLayout.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mSwipeRefreshLayout != null) {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                }
            }, 1000);
        } else {
            mSwipeRefreshLayout.setRefreshing(true);
        }
    }

    public Context context() {
        return this.getActivity();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ButterKnife.unbind(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
    }

    @Override
    protected void lazyLoad() {
        if (!isPrepared || !isVisible || mHasLoadedOnce) {
            return;
        }
        loadData(false);
    }
}
