/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 */

package com.squeezecontrol.io;

import android.net.Uri;
import android.util.Log;
import com.squeezecontrol.BrowseLoadResult;
import com.squeezecontrol.io.FutureResponse.ResponseCallback;
import com.squeezecontrol.model.Album;
import com.squeezecontrol.model.Favorite;
import com.squeezecontrol.model.Playlist;
import com.squeezecontrol.model.Song;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class SqueezePlayer {

    public enum ShuffleMode {
        NONE, BY_SONG, BY_ALBUM
    }

    private SqueezeBroker mBroker;
    private String mId;
    private String mEscapedId;
    public String name;
    public String model;
    public String ipAddress;

    private Mixer mMixer;
    private boolean mPaused = false;
    private boolean mPoweredOn = true;
    private Song mCurrentSong;
    private Long mCurrentTimeInMillis;
    private Integer mCurrentPlaylistIndex = null;
    private ShuffleMode mShuffleMode;
    private HashSet<SqueezePlayerListener> mListeners = new HashSet<SqueezePlayerListener>();

    private ResponseCallback<Song> mCurrentSongCallback;

    public SqueezePlayer(String id, SqueezeBroker broker) {
        this.mId = id;
        this.mEscapedId = Uri.encode(id);
        this.mBroker = broker;
        this.mMixer = new Mixer(this);

        mCurrentSongCallback = new FutureResponse.ResponseCallback<Song>(null) {
            @Override
            public void handleResponse(Song response) {
                mCurrentSong = response;
                songUpdated();
            }

            ;
        };
    }

    public String getId() {
        return mId;
    }

    public void addListener(SqueezePlayerListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(SqueezePlayerListener listener) {
        mListeners.remove(listener);
    }

    public void sendCommand(String command) {
        mBroker.postCommand(mEscapedId + " " + command);
    }

    public SqueezeCommand sendRequest(String request) throws IOException {
        return mBroker.sendRequest(mEscapedId + " " + request);
    }

    public void handleNotification(SqueezeCommand command) {
        String c = command.getCommand();
        try {
            if ("time".equals(c)) {
                mCurrentTimeInMillis = SqueezeCommand.parseTime(command
                        .getFirstParameter());
                if (mCurrentTimeInMillis != null) {
                    for (SqueezePlayerListener l : mListeners)
                        l.onPlayerStateChanged();
                }
            } else if ("playlist".equals(c)) {

                String firstParam = command.getFirstParameter();

                if ("index".equals(firstParam)) {
                    int indexInPlayList = Integer.valueOf(command
                            .getLastParameter());
                    updateSongIndexInPlayList(indexInPlayList);
                    getSongInPlayList(mCurrentPlaylistIndex,
                            mCurrentSongCallback);

                } else if ("newsong".equals(firstParam)) {

                    // In playlist?
                    if (command.getParameters().length == 3) {
                        int indexInPlayList = Integer.valueOf(command
                                .getLastParameter());
                        updateSongIndexInPlayList(indexInPlayList);
                        // Ask for more info
                        getSongInPlayList(mCurrentPlaylistIndex,
                                mCurrentSongCallback);
                    } else {
                        // Radio
                        updateSongIndexInPlayList(-1);
                    }
                    // Set the name for now, and wait for extra info if available
                    if (command.getParameters().length > 1) {
                        String name = command.getParameters()[1];
                        if (name != null)
                            name = SqueezeCommand.decode(name);
                        Song radio = Song.forName(name);
                        mCurrentSongCallback.handleResponse(radio);
                    }
                } else if ("shuffle".equals(firstParam)) {
                    // Response for "playlist shuffle ?"
                    int mode = Integer.parseInt(command.getLastParameter());
                    switch (mode) {
                        case 0:
                            mShuffleMode = ShuffleMode.NONE;
                            break;
                        case 1:
                            mShuffleMode = ShuffleMode.BY_SONG;
                            break;
                        case 2:
                            mShuffleMode = ShuffleMode.BY_ALBUM;
                            break;
                    }
                    notifyPlayerStateChange();
                } else if ("stop".equals(firstParam)) {
                    mCurrentSongCallback.handleResponse(null);
                }
            } else if ("pause".equals(c)) {
                // Paused / unpaused
                mPaused = "1".equals(command.getFirstParameter());
                notifyPlayerStateChange();
            } else if ("mode".equals(c)) {
                // Response for "mode ?"
                mPaused = !"play".equals(command.getFirstParameter());
                notifyPlayerStateChange();
            } else if ("power".equals(c)) {
                // Response for "power ?"
                mPoweredOn = "1".equals(command.getFirstParameter());
                notifyPlayerStateChange();
            }
        } catch (NumberFormatException e) {
            Log.e("SqueezePlayer", e.getMessage());
        }
    }

    public void requestPlayerState() throws IOException {
        // Handled async by handleNotification
        sendCommand("playlist index ?");
        sendCommand("power ?");
        sendCommand("mode ?");
        sendCommand("playlist shuffle ?");
    }

    private void notifyPlayerStateChange() {
        for (SqueezePlayerListener l : mListeners)
            l.onPlayerStateChanged();
    }

    public int getSongIndexInPlaylist() {
        if (mCurrentPlaylistIndex == null) {
            try {
                mCurrentPlaylistIndex = Integer.valueOf(sendRequest(
                        "playlist index ?").getLastParameter());
            } catch (IOException e) {
                mCurrentPlaylistIndex = -1;
            }
        }
        return mCurrentPlaylistIndex;
    }

    private void updateSongIndexInPlayList(int index) {
        if (mCurrentPlaylistIndex == null || mCurrentPlaylistIndex != index) {
            mCurrentPlaylistIndex = index;
        }
    }

    private void getSongInPlayList(int index,
                                   final FutureResponse.ResponseCallback<Song> callback) {
        String command = mEscapedId + " playlist path " + index;

        ResponseCallback<SqueezeCommand> handler = new FutureResponse.ResponseCallback<SqueezeCommand>(
                command) {
            @Override
            public void handleResponse(SqueezeCommand res) {
                String path = res.getLastParameter();
                String command = "songinfo 0 100 url%3A" + path
                        + " tags%3Aasleux";
                mBroker.postCommand(command,
                        new FutureResponse.ResponseCallback<SqueezeCommand>(
                                command) {
                            @Override
                            public void handleResponse(SqueezeCommand res) {
                                Song song = parseSong(res);
                                callback.handleResponse(song);
                            }
                        });

            }
        };
        mBroker.postCommand(command + " ?", handler);

    }

    private final Song parseSong(SqueezeCommand res) {
        Map<String, String> pMap = res.getParameterMap();
        Song song = new Song();
        song.title = pMap.get("title");
        song.artist = pMap.get("artist");
        song.album = pMap.get("album");
        song.albumId = pMap.get("album_id");
        song.artistId = pMap.get("artist_id");
        song.path = pMap.get("url");
        song.id = pMap.get("id");
        if (song.id == null) song.id = song.path;
        if (song.title == null) song.title = song.path;
        if (song.id == null)
            song = null;
        return song;
    }

    public Song getSongInPlaylist(int index) throws IOException {
        final FutureResponse<Song> response = new FutureResponse<Song>(null);
        getSongInPlayList(index,
                new FutureResponse.ResponseCallback<Song>(null) {
                    @Override
                    public void handleResponse(Song song) {
                        response.setResponse(song);
                    }
                });
        try {
            return response.getResponse();
        } catch (InterruptedException e) {
            throw new IOException("Error waiting for response: "
                    + e.getMessage());
        }
    }

    protected void songUpdated() {
        for (SqueezePlayerListener l : mListeners)
            l.onSongChanged(getCurrentSong());
    }

    public Song getCurrentSong() {
        return mCurrentSong;
    }

    public Long getCurrentTimeInMillis() {
        return mCurrentTimeInMillis;
    }

    public void pause() {
        sendCommand("pause");
        mPaused = !mPaused;
    }

    public void play() {
        sendCommand("play");
        mPaused = !mPaused;
    }

    public void togglePower() {
        if (mPoweredOn)
            powerOff();
        else
            powerOn();
    }

    public void powerOff() {
        sendCommand("power 0");
    }

    public void powerOn() {
        sendCommand("power 1");
    }

    public boolean isPowerOn() {
        return mPoweredOn;
    }

    public void skip(int seconds) {
        if (seconds == 0)
            return;
        sendCommand("time " + (seconds > 0 ? "+" : "")
                + String.valueOf(seconds));
        sendCommand("time ?");
    }

    public void nextSong() {
        sendCommand("button jump_fwd");
    }

    public void previousSong() {
        sendCommand("button jump_rew");
    }

    public boolean isPaused() {
        return mPaused;
    }

    public Mixer getMixer() {
        return mMixer;
    }

    public int getNumberOfSongsInCurrentPlayList() throws IOException {
        final FutureResponse<SqueezeCommand> resp = new FutureResponse<SqueezeCommand>(
                null);
        mBroker.postCommand(mEscapedId + " playlist tracks ?",
                new FutureResponse.ResponseCallback<SqueezeCommand>(mEscapedId
                        + " playlist tracks ") {
                    @Override
                    public void handleResponse(SqueezeCommand response) {
                        resp.setResponse(response);
                    }
                });
        try {
            return Integer.valueOf(resp.getResponse().getLastParameter());
        } catch (Exception e) {
            throw new IOException("Error getting response: " + e.getMessage());
        }
    }

    public BrowseLoadResult<Song> getSongsInCurrentPlaylist(int startIndex,
                                                            int songsToLoad) throws IOException {

        int count = getNumberOfSongsInCurrentPlayList();
        if (startIndex + songsToLoad > count)
            songsToLoad = count - startIndex;

        final ArrayList<Song> songs = new ArrayList<Song>(songsToLoad);
        final CountDownLatch latch = new CountDownLatch(songsToLoad);

        final FutureResponse.ResponseCallback<Song> callback = new ResponseCallback<Song>(
                null) {
            @Override
            public void handleResponse(Song song) {
                songs.add(song);
                // Log.i("Player", "R: " + songs.size());
                latch.countDown();
            }
        };

        int endIndex = startIndex + songsToLoad;
        for (int i = startIndex; i < endIndex; i++) {
            getSongInPlayList(i, callback);
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
        }
        return new BrowseLoadResult<Song>(count, startIndex, songs);
    }

    public void setSongIndexInPlayList(int position) {
        sendCommand("playlist index " + position);
    }

    public ShuffleMode getShuffleMode() {
        return mShuffleMode;
    }

    public void setShuffleMode(ShuffleMode mode) {
        sendCommand("playlist shuffle " + mode.ordinal());
    }

    public void addToPlaylist(Song song) {
        sendCommand("playlist addtracks track.id=" + song.getId());
    }

    public void playNow(Song song) {
        sendCommand("playlist loadtracks track.id=" + song.getId());
    }

    public void playNow(Playlist playlist) {
        sendCommand("playlist loadtracks playlist.id="
                + Uri.encode(playlist.id));
    }

    public void addToPlaylist(Playlist playlist) {
        sendCommand("playlist addtracks playlist.id=" + Uri.encode(playlist.id));
    }

	public void addToPlaylist(Album album) {
		sendCommand("playlist addtracks album.id=" + album.id);
	}

    public void playNow(Album album) {
        sendCommand("playlist loadtracks album.id=" + album.id);
    }

    public void playNow(Favorite favorite) {
        sendCommand("playlist play " + Uri.encode(favorite.url) + " "
                + favorite.name);
    }

    public void addToPlaylist(Favorite favorite) {
        sendCommand("playlist add " + Uri.encode(favorite.url));
    }

    public void removeFromPlaylist(int index) {
        if (mCurrentPlaylistIndex > index)
            mCurrentPlaylistIndex--;
        sendCommand("playlist delete " + index);
    }

}
