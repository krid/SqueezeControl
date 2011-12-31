/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 */

package com.squeezecontrol;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.util.Linkify;
import android.view.ContextMenu;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.squeezecontrol.image.ImageLoaderService;
import com.squeezecontrol.model.Album;
import com.squeezecontrol.model.Browsable;
import com.squeezecontrol.model.HasArtist;
import com.squeezecontrol.model.Song;
import com.squeezecontrol.view.BrowseableAdapter;

import java.io.IOException;

public class SongBrowserActivity extends AbstractMusicBrowserActivity<Song> {

    public static final String EXTRA_ALBUM_OBJECT = "album_object";
    public static String EXTRA_ARTIST_ID = "artist_id";

    private String mAlbumId = null;
    private String mArtistId = null;
    private Album mAlbum = null;

    private View mheader = null;
    private ImageLoaderService mCoverImageService;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTitle = "song";

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
        	mAlbum = (Album) extras.getSerializable(EXTRA_ALBUM_OBJECT);
            mAlbumId = null == mAlbum ? null : mAlbum.id;
            mArtistId = extras.getString(EXTRA_ARTIST_ID);
        }

        setContentView(R.layout.default_browser_list);

        if (null != mAlbum) {
        	// Add a header to the song list showing the album icon/title.
        	hasHeaderOrFooter = true;
        	headerOrFooterPosition = 0;

	        mheader = getLayoutInflater().inflate(R.layout.play_album_header, null);
	        TextView albumTitleView = (TextView) mheader.findViewById(R.id.album_name);
	        albumTitleView.setText("Album: " + mAlbum.name);

	        ListView listView = getListView();
			listView.addHeaderView(mheader);
        }
        
        super.init();
    }

    /**
     * Try to load the album cover image for the list header.
     */
    private void loadAlbumImage() {
    	ImageView mCoverImage = (ImageView) mheader.findViewById(R.id.album_image);
        if (null == mAlbum.artwork_track_id) {
        	// No cover image available
            mCoverImage.setImageResource(R.drawable.unknown_album_cover);
        } else {
        	/* In most cases the cover image will be in the cache, since
        	 * the user was just viewing the album list.  If not, we'll
        	 * have to load it... */
            Bitmap image = mCoverImageService.getFromCache(mAlbum);
            if (image != null) {
                mCoverImage.setImageBitmap(image);
            } else {
            	// Set temp image...
                mCoverImage.setImageResource(R.drawable.unknown_album_cover);
                // and load the real one
                // FIXME This is a lot of code, and I'm not sure how often
                // we'll need to do it.  Postpone the work...
                //mCoverImageService.loadImage(mAlbum, mImageCallback);
            }
        }
    }

    @Override
    protected void onServiceBound(SqueezeService service) {
        super.onServiceBound(service);
        if (hasHeaderOrFooter) {
        	mCoverImageService = service.getCoverImageService();
        	loadAlbumImage();
        }
    }

    @Override
    protected BrowseableAdapter<Song> createListAdapter() {
        return new BrowseableAdapter<Song>(this, R.layout.song_list_item) {
            @Override
            protected void bindView(int position, View view) {
                Song s = getItem(position);

                TextView songName = (TextView) view.findViewById(R.id.name);
                TextView artistName = (TextView) view
                        .findViewById(R.id.artist_name);
                TextView albumName = (TextView) view
                        .findViewById(R.id.album_name);

                if (s == null) {
                    songName.setText(R.string.loading_progress);
                    artistName.setText("");
                    albumName.setText("");
                } else {
                    songName.setText(s.getName());
                    if (getQueryPattern() != null) {
                        Linkify.addLinks(songName, getQueryPattern(), null);
                    }
                    artistName.setText(s.artist);
                    albumName.setText(s.album);
                }

            }
        };
    }

    @Override
    protected void addContextMenuItems(ContextMenu menu, Browsable item) {
    	menu.add(0, ARTIST_CTX_MENU_ITEM, 1, "Artist: " + ((HasArtist) item).getArtistName());
    	if (item instanceof Song) {
    		Song s = (Song) item;
    		menu.add(0, ALBUM_CTX_MENU_ITEM, 1, "Album: " + s.album);
    	}
		menu.add(0, DOWNLOAD_CTX_MENU_ITEM, 1, "Download to device");
    }

    @Override
    protected Browsable getHeaderOrFooterItem() {
    	return mAlbum;
    }
    
    @Override
    protected void handleHeaderOrFooterContextClick(boolean isAdd) {
    	if (isAdd) {
    		// Add album to playlist
    		getPlayer().addToPlaylist(mAlbum);
    		PlayerToasts.addedToPlayList(this, mAlbum);
    	} else {
    		// Play album now
    		getPlayer().playNow(mAlbum);
    	}
	}

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (position < 0)
            return;
        if (hasHeaderOrFooter && position == headerOrFooterPosition) {
        	getPlayer().addToPlaylist(mAlbum);
        	PlayerToasts.addedToPlayList(this, mAlbum);
        } else {
	        Song item = (Song) l.getItemAtPosition(position);
	        if (item == null)
	            return;
	        addToPlaylist(item);
	    }
    }

    @Override
    protected void addToPlaylist(Song selectedItem) {
        getPlayer().addToPlaylist(selectedItem);
        PlayerToasts.addedToPlayList(this, selectedItem);
    }

    @Override
    protected void play(Song selectedItem, int index) {
        getPlayer().playNow(selectedItem);
    }

    @Override
    protected BrowseLoadResult<Song> loadItems(int startIndex, int count)
            throws IOException {
        return getMusicBrowser().getSongs(getQueryString(), mAlbumId,
                mArtistId, startIndex, count);
    }

}
