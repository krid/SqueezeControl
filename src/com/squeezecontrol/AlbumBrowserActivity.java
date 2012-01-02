/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 */

package com.squeezecontrol;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.util.Linkify;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.View;
import android.widget.*;
import android.widget.AbsListView.OnScrollListener;
import com.squeezecontrol.image.ImageLoaderService;
import com.squeezecontrol.model.Album;
import com.squeezecontrol.model.Browsable;
import com.squeezecontrol.view.BrowseableAdapter;

import java.io.IOException;

public class AlbumBrowserActivity extends AbstractMusicBrowserActivity<Album>
        implements ListView.OnScrollListener {

    public static final String EXTRA_ARTIST_ID = "artist_id";
    public static final String EXTRA_SORT_MODE = "sort_mode";

    public static final String SORT_MODE_ALBUM = "album";
    public static final String SORT_MODE_NEW_MUSIC = "new";

    private String mArtistId;
    private String mSortMode = SORT_MODE_ALBUM;
    private ImageLoaderService mCoverImageService;

    /**
     * When a scroll fling is ongoing we don't want to load images. 
     */
    private boolean mScrollFlingInProgress = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTitle = "album";
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mArtistId = extras.getString(EXTRA_ARTIST_ID);
            mSortMode = extras.getString(EXTRA_SORT_MODE);
            if (mSortMode == null)
                mSortMode = SORT_MODE_ALBUM;
        }

        setContentView(R.layout.default_browser_list);

        getListView().setOnScrollListener(this);

        super.init();
    }

    @Override
    protected void onServiceBound(SqueezeService service) {
        super.onServiceBound(service);
        mCoverImageService = service.getCoverImageService();
    }

    /* Scrolling behavior explained...
     * 
     * bindView is called for every list item during any kind of scrolling
     * (and also when the view is first loaded). 
     * 
     * During slow scrolling (SCROLL_STATE_TOUCH_SCROLL) bindView is called
     * and getItem() returns a non-null Album.
     * 
     * During fast scrolling (SCROLL_STATE_TOUCH_SCROLL using the 'thumb'
     * or SCROLL_STATE_FLING) bindView is called but getItem() returns a null
     * Album (presumably because LoaderThread is backed up), so we just use the
     * placeholder data for everything.
     */
    @Override
    protected BrowseableAdapter<Album> createListAdapter() {
        return new BrowseableAdapter<Album>(this, R.layout.album_list_item) {
            @Override
            protected void bindView(int position, View view) {
                Album a = getItem(position);

                TextView albumName = (TextView) view
                        .findViewById(R.id.album_name);
                TextView artistName = (TextView) view
                        .findViewById(R.id.album_artist_name);
                ImageView coverImage = (ImageView) view
                        .findViewById(R.id.album_image);

                if (a == null) {
                    albumName.setText(R.string.loading_progress);
                    artistName.setText("");
                    coverImage.setImageResource(R.drawable.unknown_album_cover);
                } else {
                    if (a.artwork_track_id == null) {
                    	// No art for this album
                        coverImage.setImageResource(R.drawable.unknown_album_cover);
                    } else {
                        Bitmap image = mCoverImageService.getFromCache(a);
                        if (image != null) {
                        	// Cached image, use immediately
                            coverImage.setImageBitmap(image);
                        } else {
                        	/* Slap up a placeholder and, if we're not still
                        	 * scrolling, start an async image fetch. */
                            coverImage.setImageResource(R.drawable.unknown_album_cover);
                            if (! mScrollFlingInProgress) {
                                mCoverImageService.loadImage(a, coverImage);
                            }

                        }
                    }
                    albumName.setText(a.getName());
                    if (getQueryPattern() != null) {
                        Linkify.addLinks(albumName, getQueryPattern(), null);
                    }
                    artistName.setText(a.artistName);
                }
            }
        };
    }

    @Override
    protected void addContextMenuItems(ContextMenu menu, Browsable item) {
    	Album album = (Album) item;
		menu.add(0, ARTIST_CTX_MENU_ITEM, 1, "Artist: " + album.artistName);
		menu.add(0, DOWNLOAD_CTX_MENU_ITEM, 1, "Download to device");
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (position < 0)
            return;
        Album selectedItem = (Album) getListAdapter().getItem(position);
        if (selectedItem == null) return;

        Intent intent = new Intent(this, SongBrowserActivity.class);
        intent.putExtra(SongBrowserActivity.EXTRA_ALBUM_OBJECT, selectedItem);
        
        if (mArtistId != null)
            intent.putExtra(SongBrowserActivity.EXTRA_ARTIST_ID, mArtistId);
        startActivity(intent);
    }

    @Override
    protected void addToPlaylist(Album selectedItem) {
        getPlayer().addToPlaylist(selectedItem);
        PlayerToasts.addedToPlayList(this, selectedItem);
     }

    @Override
    protected void play(Album selectedItem, int index) {
        getPlayer().playNow(selectedItem);
    }

    protected int getItemCount() {
        return getMusicBrowser().getAlbumCount(getQueryString(), mArtistId);
    }

    @Override
    protected BrowseLoadResult<Album> loadItems(int startIndex, int count)
            throws IOException {

        return getMusicBrowser().getAlbums(getQueryString(), mArtistId,
                startIndex, count, mSortMode);
    }

    /*
     * Note that when you stop a fling scroll by tapping on the screen the
     * scroll state changes *twice* -- from FLING to TOUCH_SCROLL, and then
     * from TOUCH_SCROLL to IDLE.
     * 
     * (non-Javadoc)
     * @see com.squeezecontrol.AbstractMusicBrowserActivity#onScrollStateChanged(android.widget.AbsListView, int)
     */
    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        super.onScrollStateChanged(view, scrollState);
        if (scrollState == OnScrollListener.SCROLL_STATE_FLING) {
        	mScrollFlingInProgress = true;
        } else if (mScrollFlingInProgress) {
        	// Fling scroll has wound down or been stopped by a tap, load images.
        	mScrollFlingInProgress = false;
        	// Call notifyDataSetChanged for its side effect of refreshing the
        	// list view.
        	getListAdapter().notifyDataSetChanged();
        }
    }
}
