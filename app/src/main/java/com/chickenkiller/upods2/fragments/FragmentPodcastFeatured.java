package com.chickenkiller.upods2.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.chickenkiller.upods2.R;
import com.chickenkiller.upods2.activity.ActivityPlayer;
import com.chickenkiller.upods2.controllers.adaperts.MediaItemsAdapter;
import com.chickenkiller.upods2.controllers.app.ProfileManager;
import com.chickenkiller.upods2.controllers.app.SettingsManager;
import com.chickenkiller.upods2.controllers.internet.BackendManager;
import com.chickenkiller.upods2.interfaces.IContentLoadListener;
import com.chickenkiller.upods2.interfaces.IFragmentsManager;
import com.chickenkiller.upods2.interfaces.IMediaItemView;
import com.chickenkiller.upods2.interfaces.IRequestCallback;
import com.chickenkiller.upods2.interfaces.ISlidingMenuHolder;
import com.chickenkiller.upods2.interfaces.IToolbarHolder;
import com.chickenkiller.upods2.models.MediaItemTitle;
import com.chickenkiller.upods2.models.Podcast;
import com.chickenkiller.upods2.utils.ServerApi;
import com.chickenkiller.upods2.utils.enums.MediaItemType;
import com.chickenkiller.upods2.views.AutofitRecyclerView;
import com.chickenkiller.upods2.views.GridSpacingItemDecoration;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by Alon Zilberman on 7/10/15.
 */
public class FragmentPodcastFeatured extends Fragment implements IContentLoadListener {

    public static final String TAG = "podcasts_featured";
    public static final int MEDIA_ITEMS_TYPES_COUNT = 2;

    private AutofitRecyclerView rvMain;
    private MediaItemsAdapter mediaItemsAdapter;
    private ProgressBar pbLoadingFeatured;
    private LinearLayout lnInternetError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((IToolbarHolder) getActivity()).getToolbar().setVisibility(View.VISIBLE);

        //Init fragments views
        View view = inflater.inflate(R.layout.fragment_podcasts_featured, container, false);
        lnInternetError = (LinearLayout) view.findViewById(R.id.lnInternetError);
        pbLoadingFeatured = (ProgressBar) view.findViewById(R.id.pbLoadingFeatured);
        rvMain = (AutofitRecyclerView) view.findViewById(R.id.rvMain);

        //Toolbar
        if (getActivity() instanceof IToolbarHolder) {
            MenuItem searchMenuItem = ((IToolbarHolder) getActivity()).getToolbar().getMenu().findItem(R.id.action_search);
            searchMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    FragmentSearch fragmentSearch = new FragmentSearch();
                    fragmentSearch.setSearchType(MediaItemType.PODCAST);
                    ((IFragmentsManager) getActivity()).showFragment(R.id.fl_content, fragmentSearch, FragmentSearch.TAG);
                    return false;
                }
            });
        }
        ((IToolbarHolder) getActivity()).getToolbar().setTitle(R.string.podcasts_main);
        ((ISlidingMenuHolder) getActivity()).setSlidingMenuHeader(getString(R.string.podcasts));

        //Featured adapter
        mediaItemsAdapter = new MediaItemsAdapter(getActivity(), R.layout.card_media_item_vertical,
                R.layout.media_item_title);
        if (getActivity() instanceof IFragmentsManager) {
            mediaItemsAdapter.setFragmentsManager((IFragmentsManager) getActivity());
        }
        mediaItemsAdapter.setContentLoadListener(this);

        //Featured recycle view
        rvMain.setHasFixedSize(true);
        rvMain.setAdapter(mediaItemsAdapter);
        rvMain.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                int viewType = mediaItemsAdapter.getItemViewType(position);
                return (viewType != MediaItemsAdapter.HEADER && viewType != MediaItemsAdapter.BANNERS_LAYOUT) ?
                        1 : rvMain.getSpanCount();
            }
        });
        mediaItemsAdapter.notifyContentLoadingStatus();
        rvMain.setVisibility(View.INVISIBLE);

        //Load tops from remote server
        showTops();

        //Open search fragment backed from other activity which was started from search
        if (getActivity().getIntent().hasExtra(ActivityPlayer.ACTIVITY_STARTED_FROM_IN_DEPTH)) {
            FragmentSearch.openFromIntent(getActivity());
        }
        return view;
    }

    private void showTops() {
        String topLang = SettingsManager.getInstace().getStringSettingValue(SettingsManager.JS_TOPS_LANGUAGE);
        BackendManager.TopType topType = topLang.equals(SettingsManager.TOPS_RU_LANGUAGE) ? BackendManager.TopType.MAIN_PODCAST_RU
                : BackendManager.TopType.MAIN_PODCAST;

        BackendManager.getInstance().loadTops(topType, ServerApi.PODCASTS_TOP, new IRequestCallback() {
                    @Override
                    public void onRequestSuccessed(final JSONObject jResponse) {
                        try {
                            final ArrayList<IMediaItemView> topPodcasts = new ArrayList<IMediaItemView>();
                            ArrayList<Podcast> topPodcastsItems = Podcast.withJsonArray(jResponse.getJSONArray("result"));
                            Podcast.syncWithDb(topPodcastsItems);
                            MediaItemTitle mediaItemTitle = new MediaItemTitle(getString(R.string.top40_podcasts), getString(R.string.top40_podcasts_long));
                            mediaItemTitle.showButton = true;
                            topPodcasts.add(mediaItemTitle);
                            topPodcasts.addAll(topPodcastsItems);

                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mediaItemsAdapter.addItems(topPodcasts);
                                    rvMain.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            GridSpacingItemDecoration gridSpacingItemDecoration = new GridSpacingItemDecoration(rvMain.getSpanCount(), FragmentMainFeatured.MEDIA_ITEMS_CARDS_MARGIN, true);
                                            gridSpacingItemDecoration.setGridItemType(MediaItemsAdapter.ITEM);
                                            gridSpacingItemDecoration.setItemsTypesCount(MEDIA_ITEMS_TYPES_COUNT);
                                            rvMain.addItemDecoration(gridSpacingItemDecoration);
                                            mediaItemsAdapter.notifyContentLoadingStatus();
                                        }
                                    });
                                }
                            });
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onRequestFailed() {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                pbLoadingFeatured.setVisibility(View.GONE);
                                rvMain.setVisibility(View.GONE);
                                lnInternetError.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }
        );
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onContentLoaded() {
        pbLoadingFeatured.setVisibility(View.GONE);
        rvMain.setVisibility(View.VISIBLE);
    }

    public void notifyMediaItemChanges(ProfileManager.ProfileUpdateEvent profileUpdateEvent) {
        if (mediaItemsAdapter != null && profileUpdateEvent.mediaItem instanceof Podcast) {
            mediaItemsAdapter.updateMediaItem(profileUpdateEvent.mediaItem);
        }
    }

    public void notifyDataChanged() {
        if (mediaItemsAdapter != null) {
            mediaItemsAdapter.notifyDataSetChanged();
        }
    }
}
