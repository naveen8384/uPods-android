package com.chickenkiller.upods2.controllers;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.LayerDrawable;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.chickenkiller.upods2.R;
import com.chickenkiller.upods2.interfaces.IContentLoadListener;
import com.chickenkiller.upods2.interfaces.IFeaturableMediaItem;
import com.chickenkiller.upods2.interfaces.IFragmentsManager;
import com.chickenkiller.upods2.interfaces.IPlayableMediaItem;
import com.chickenkiller.upods2.models.BannersLayoutItem;
import com.chickenkiller.upods2.models.MediaItem;
import com.chickenkiller.upods2.models.MediaItemTitle;
import com.chickenkiller.upods2.models.ViewHolderBannersLayout;
import com.chickenkiller.upods2.view.controller.FragmentMediaItemDetails;
import com.chickenkiller.upods2.views.ImageViewSquare;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by alonzilberman on 7/2/15.
 */
public class MediaItemsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int HEADER = 1;
    public static final int ITEM = 2;
    public static final int BANNERS_LAYOUT = 3;
    private static final int MAX_CONTENT_LEVEL = 2; //count of items to load (banner, main cards)

    private int itemLayout;
    private int titleLayout;
    private int currentContentLevel;
    private boolean needDestroy;

    private List<MediaItem> items;
    private Context mContext;
    private IFragmentsManager fragmentsManager;
    private IContentLoadListener iContentLoadListener;

    private static class ViewHolderCardItem extends RecyclerView.ViewHolder {
        public ImageViewSquare imgSquare;
        public TextView tvSquareTitle;
        public RatingBar rbMediaItem;
        public CardView cvSquare;

        public ViewHolderCardItem(View view) {
            super(view);
            this.imgSquare = (ImageViewSquare) view.findViewById(R.id.imgSquare);
            this.tvSquareTitle = (TextView) view.findViewById(R.id.tvSquareTitle);
            this.rbMediaItem = (RatingBar) view.findViewById(R.id.rbMediaItem);
            this.cvSquare = (CardView) view;
            Context context = view.getContext();
            LayerDrawable stars = (LayerDrawable) rbMediaItem.getProgressDrawable();
            stars.getDrawable(2).setColorFilter(context.getResources().getColor(R.color.starFullySelected), PorterDuff.Mode.SRC_ATOP);
            stars.getDrawable(1).setColorFilter(context.getResources().getColor(R.color.starPartiallySelected), PorterDuff.Mode.SRC_ATOP);
            stars.getDrawable(0).setColorFilter(context.getResources().getColor(R.color.starNotSelected), PorterDuff.Mode.SRC_ATOP);
        }

        public void setCardClickListener(View.OnClickListener cardClickListener) {
            cvSquare.setOnClickListener(cardClickListener);
        }

    }

    private static class ViewHolderMediaItemTitle extends RecyclerView.ViewHolder {
        public TextView tvMediaCardTitle;
        public TextView tvMediaCardSubTitle;

        public ViewHolderMediaItemTitle(View view) {
            super(view);
            this.tvMediaCardTitle = (TextView) view.findViewById(R.id.tvMediaCardTitle);
            this.tvMediaCardSubTitle = (TextView) view.findViewById(R.id.tvMediaCardSubTitle);
        }

    }

    public MediaItemsAdapter(Context mContext, int itemLayout, List<MediaItem> items) {
        super();
        this.items = items;
        this.itemLayout = itemLayout;
        this.mContext = mContext;
    }

    public MediaItemsAdapter(Context mContext, int itemLayout, int titleLayout, ArrayList<MediaItem> items) {
        this(mContext, itemLayout, items);
        this.titleLayout = titleLayout;
    }

    public MediaItemsAdapter(Context mContext, int itemLayout, int titleLayout) {
        this(mContext, itemLayout, new ArrayList<MediaItem>());
        this.titleLayout = titleLayout;
    }

    public void setFragmentsManager(IFragmentsManager fragmentsManager) {
        this.fragmentsManager = fragmentsManager;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        RecyclerView.ViewHolder viewHolder = null;
        if (viewType == BANNERS_LAYOUT) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.rv_banners_layout, parent, false);
            IContentLoadListener contentLoadListener = new IContentLoadListener() {
                @Override
                public void onContentLoaded() {
                    notifyContentLoadingStatus();
                }
            };
            viewHolder = new ViewHolderBannersLayout(view, fragmentsManager, contentLoadListener);
            currentContentLevel = 0;
        } else if (viewType == ITEM) {
            view = LayoutInflater.from(parent.getContext()).inflate(itemLayout, parent, false);
            viewHolder = new ViewHolderCardItem(view);
        } else {
            view = LayoutInflater.from(parent.getContext()).inflate(titleLayout, parent, false);
            viewHolder = new ViewHolderMediaItemTitle(view);
        }
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof ViewHolderCardItem) {//Card
            IFeaturableMediaItem currentItem = (IFeaturableMediaItem) items.get(position);
            Glide.with(mContext).load(currentItem.getCoverImageUrl()).centerCrop()
                    .crossFade().into(((ViewHolderCardItem) holder).imgSquare);
            ((ViewHolderCardItem) holder).tvSquareTitle.setText(currentItem.getName());
            holder.itemView.setTag(currentItem);
            ((ViewHolderCardItem) holder).setCardClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    FragmentMediaItemDetails fragmentMediaItemDetails = new FragmentMediaItemDetails();
                    if (items.get(position) instanceof IPlayableMediaItem) {
                        fragmentMediaItemDetails.setPlayableItem((IPlayableMediaItem) items.get(position));
                    }
                    if (!fragmentsManager.hasFragment(FragmentMediaItemDetails.TAG)) {
                        fragmentsManager.showFragment(R.id.fl_window, fragmentMediaItemDetails, FragmentMediaItemDetails.TAG,
                                IFragmentsManager.FragmentOpenType.OVERLAY, IFragmentsManager.FragmentAnimationType.BOTTOM_TOP);
                    }
                }
            });
        } else if (holder instanceof ViewHolderMediaItemTitle) {//Title
            MediaItemTitle currentItem = (MediaItemTitle) items.get(position);
            ((ViewHolderMediaItemTitle) holder).tvMediaCardTitle.setText(currentItem.getTitle());
            ((ViewHolderMediaItemTitle) holder).tvMediaCardSubTitle.setText(currentItem.getSubTitle());
            holder.itemView.setTag(currentItem);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof BannersLayoutItem)
            return BANNERS_LAYOUT;
        if (items.get(position) instanceof MediaItemTitle)
            return HEADER;

        return ITEM;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void addItems(ArrayList<MediaItem> items) {
        this.items.addAll(items);
        this.notifyDataSetChanged();
    }

    public void setContentLoadListener(IContentLoadListener contentLoadListener) {
        this.iContentLoadListener = contentLoadListener;
    }


    /**
     * Function to synchronize adapter content loading . Call it every time, when part of content was loaded.
     * When all content will be loaded  IContentLoadListener callback will fire.
     */
    public void notifyContentLoadingStatus() {
        currentContentLevel++;
        if (currentContentLevel == MAX_CONTENT_LEVEL && iContentLoadListener != null) {
            iContentLoadListener.onContentLoaded();
        }
    }

    public void destroy() {
        this.needDestroy = true;
    }

}
