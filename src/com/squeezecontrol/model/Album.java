/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 */

package com.squeezecontrol.model;

public class Album implements Browsable, HasArtist {
	private static final long serialVersionUID = -4759544259646942401L;
	public String id;
    public String name;
    public String artistName;
    public String artwork_track_id;

    public String getName() {
        return name;
    }

    public static Album forSong(Song song) {
    	Album a = new Album();
        a.id = song.getAlbumId();
        a.artistName = song.artist;
        a.name = song.album;
        return a;
    }
    
    public static Album forName(String name) {
        Album a = new Album();
        a.name = name;
        return a;
    }

    @Override
    public String toString() {
        return name;
    }

	@Override
	public String getArtistName() {
		return artistName;
	}
}
