package com.chickenkiller.upods2.controllers.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;

import com.chickenkiller.upods2.controllers.database.SQLdatabaseManager;
import com.chickenkiller.upods2.interfaces.IOperationFinishWithDataCallback;
import com.chickenkiller.upods2.models.Episode;
import com.chickenkiller.upods2.models.Feed;
import com.chickenkiller.upods2.models.MediaItem;
import com.chickenkiller.upods2.models.MediaListItem;
import com.chickenkiller.upods2.models.Podcast;
import com.chickenkiller.upods2.models.RadioItem;
import com.chickenkiller.upods2.models.Track;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by Alon Zilberman on 9/28/15.
 */
public class ProfileManager {

    public static final String JS_SUBSCRIBED_PODCASTS = "subscribedPodcasts";
    public static final String JS_SUBSCRIBED_STATIONS = "subscribedStations";
    public static final String JS_RECENT_STATIONS = "recentStations";

    public static ProfileManager profileManager;
    private IOperationFinishWithDataCallback profileSavedCallback;


    public static class ProfileUpdateEvent {
        public String updateListType;
        public MediaItem mediaItem;
        public boolean isRemoved;

        public ProfileUpdateEvent(String updateListType, MediaItem mediaItem, boolean isRemoved) {
            this.updateListType = updateListType;
            this.mediaItem = mediaItem;
            this.isRemoved = isRemoved;
        }
    }

    private ProfileManager() {

    }

    public synchronized static ProfileManager getInstance() {
        if (profileManager == null) {
            profileManager = new ProfileManager();
        }
        return profileManager;
    }

    public void setProfileSavedCallback(IOperationFinishWithDataCallback profileSavedCallback) {
        this.profileSavedCallback = profileSavedCallback;
    }

    private ArrayList<Long> getMediaListIds(String mediaType, String listType) {
        SQLiteDatabase database = UpodsApplication.getDatabaseManager().getReadableDatabase();
        String[] args1 = {mediaType, listType};
        ArrayList<Long> ids = new ArrayList<>();
        String table = mediaType.equals(MediaListItem.TYPE_RADIO) ? "radio_stations" : "podcasts";
        Cursor cursor = database.rawQuery("SELECT p.id FROM " + table + " as p\n" +
                "LEFT JOIN media_list as ml\n" +
                "ON p.id = ml.media_id\n" +
                "WHERE ml.media_type = ? and ml.list_type = ?", args1);
        while (cursor.moveToNext()) {
            ids.add(cursor.getLong(cursor.getColumnIndex("id")));
        }
        cursor.close();
        return ids;
    }

    private ArrayList<Podcast> getPodcastsForMediaType(String mediaType) {
        SQLiteDatabase database = UpodsApplication.getDatabaseManager().getReadableDatabase();
        ArrayList<Long> ids = getMediaListIds(MediaListItem.TYPE_PODCAST,
                mediaType.equals(MediaListItem.DOWNLOADED) ? MediaListItem.SUBSCRIBED : MediaListItem.DOWNLOADED);
        ArrayList<Podcast> podcasts = new ArrayList<>();
        String[] args2 = {MediaListItem.TYPE_PODCAST, mediaType};
        Cursor cursor = database.rawQuery("SELECT p.* FROM podcasts as p\n" +
                "LEFT JOIN media_list as ml\n" +
                "ON p.id = ml.media_id\n" +
                "WHERE ml.media_type = ? and ml.list_type = ? " +
                "ORDER BY id DESC", args2);
        while (cursor.moveToNext()) {
            Podcast podcast = Podcast.withCursor(cursor);
            if (mediaType.equals(MediaListItem.DOWNLOADED)) {
                podcast.isDownloaded = true;
                if (ids.contains(podcast.id)) {
                    podcast.isSubscribed = true;
                }
            } else if (mediaType.equals(MediaListItem.SUBSCRIBED)) {
                podcast.isSubscribed = true;
                if (ids.contains(podcast.id)) {
                    podcast.isDownloaded = true;
                }
            }
            podcasts.add(podcast);
        }
        cursor.close();

        Podcast.syncWithDb(podcasts);

        return podcasts;
    }

    private ArrayList<RadioItem> getRadioStationsForMediaType(String mediaType) {
        SQLiteDatabase database = UpodsApplication.getDatabaseManager().getReadableDatabase();
        ArrayList<Long> ids = getMediaListIds(MediaListItem.TYPE_RADIO,
                mediaType.equals(MediaListItem.RECENT) ? MediaListItem.SUBSCRIBED : MediaListItem.RECENT);
        ArrayList<RadioItem> radioItems = new ArrayList<>();
        String[] args2 = {MediaListItem.TYPE_RADIO, mediaType};

        Cursor cursor = database.rawQuery("SELECT r.* FROM radio_stations as r\n" +
                "LEFT JOIN media_list as ml\n" +
                "ON r.id = ml.media_id\n" +
                "WHERE ml.media_type = ? and ml.list_type = ? " +
                "ORDER BY id DESC", args2);

        while (cursor.moveToNext()) {
            RadioItem radioItem = RadioItem.withCursor(cursor);
            if (mediaType.equals(MediaListItem.RECENT)) {
                radioItem.isRecent = true;
                if (ids.contains(radioItem.id)) {
                    radioItem.isSubscribed = true;
                }
            } else if (mediaType.equals(MediaListItem.SUBSCRIBED)) {
                radioItem.isSubscribed = true;
                if (ids.contains(radioItem.id)) {
                    radioItem.isRecent = true;
                }
            }
            radioItems.add(radioItem);
        }
        cursor.close();
        return radioItems;
    }

    private void addTrack(MediaItem mediaItem, Track track, String listType) {
        if (mediaItem instanceof Podcast && track instanceof Episode) {
            SQLiteDatabase database = UpodsApplication.getDatabaseManager().getWritableDatabase();
            boolean isNotifyChanges = false;
            Podcast podcast = (Podcast) mediaItem;
            Episode episode = (Episode) track;

            //1. Save episode/podcast if needed
            if (!podcast.isExistsInDb) {
                podcast.save();
            }
            if (!episode.isExistsInDb) {
                episode.save(podcast.id);
            }

            //2. Add podcast to media_list if needed
            if ((!podcast.isDownloaded && listType.equals(MediaListItem.DOWNLOADED)) ||
                    (!podcast.hasNewEpisodes && listType.equals(MediaListItem.NEW))) {
                ContentValues values = new ContentValues();
                values.put("media_id", mediaItem.id);
                values.put("media_type", MediaListItem.TYPE_PODCAST);
                values.put("list_type", listType);
                database.insert("media_list", null, values);
                isNotifyChanges = true;
            }

            //3. Create podcasts_episodes_rel
            if ((!episode.isDownloaded && listType.equals(MediaListItem.DOWNLOADED)) ||
                    (!episode.isNew && listType.equals(MediaListItem.NEW))) {
                ContentValues values = new ContentValues();
                values.put("podcast_id", podcast.id);
                values.put("episode_id", episode.id);
                values.put("type", listType);
                database.insert("podcasts_episodes_rel", null, values);
            }

            //4. Update objects
            if (listType.equals(MediaListItem.DOWNLOADED)) {
                podcast.isDownloaded = true;
                episode.isDownloaded = true;
                if (isNotifyChanges) {
                    notifyChanges(new ProfileUpdateEvent(MediaListItem.DOWNLOADED, mediaItem, false));
                }
            } else if (listType.equals(MediaListItem.NEW)) {
                podcast.hasNewEpisodes = true;
                episode.isNew = true;
                if (!Podcast.hasEpisodeWithTitle(podcast, episode)) {
                    podcast.getEpisodes().add(episode);
                }
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    notifyChanges(new ProfileUpdateEvent(MediaListItem.NEW, mediaItem, false));
                }
            }
        }
    }

    private void removeTrack(MediaItem mediaItem, Track track, String listType) {
        if (mediaItem instanceof Podcast && track instanceof Episode) {
            SQLiteDatabase database = UpodsApplication.getDatabaseManager().getWritableDatabase();
            Podcast podcast = (Podcast) mediaItem;
            Episode episode = (Episode) track;

            //Remove reletionships -> then remove episode
            String args[] = {String.valueOf(podcast.id), String.valueOf(episode.id), listType};
            database.delete("podcasts_episodes_rel", "podcast_id = ? AND episode_id = ? AND type = ?", args);

            if (listType.equals(MediaListItem.DOWNLOADED)) {
                File episodeFile = new File(episode.getAudeoUrl());
                episodeFile.delete();
                episode.isDownloaded = false;
                if (!episode.isNew) {
                    episode.remove();
                }
            } else if (listType.equals(MediaListItem.NEW)) {
                episode.isNew = false;
                if (!episode.isDownloaded) {
                    episode.remove();
                }
            }

            if (listType.equals(MediaListItem.DOWNLOADED) && podcast.getDownloadedEpisodsCount() == 0) {
                String args2[] = {String.valueOf(podcast.id), MediaListItem.TYPE_PODCAST, MediaListItem.DOWNLOADED};
                database.delete("media_list", "media_id = ? AND media_type = ? AND list_type = ?", args2);
                podcast.isDownloaded = false;
                notifyChanges(new ProfileUpdateEvent(MediaListItem.DOWNLOADED, mediaItem, true));
            } else if (listType.equals(MediaListItem.NEW) && podcast.getNewEpisodsCount() == 0) {
                podcast.hasNewEpisodes = false;
                String args2[] = {String.valueOf(podcast.id), MediaListItem.TYPE_PODCAST, MediaListItem.NEW};
                database.delete("media_list", "media_id = ? AND media_type = ? AND list_type = ?", args2);
            }

            if (listType.equals(MediaListItem.NEW) && Looper.myLooper() == Looper.getMainLooper()) {
                //If we are in main thread and were changes in count of new episodes -> notify provider
                notifyChanges(new ProfileUpdateEvent(MediaListItem.NEW, mediaItem, false));
            }

        }
    }

    public ArrayList<Podcast> getDownloadedPodcasts() {
        return getPodcastsForMediaType(MediaListItem.DOWNLOADED);
    }

    public ArrayList<Podcast> getSubscribedPodcasts() {
        return getPodcastsForMediaType(MediaListItem.SUBSCRIBED);
    }

    public ArrayList<RadioItem> getSubscribedRadioItems() {
        return getRadioStationsForMediaType(MediaListItem.SUBSCRIBED);
    }

    public ArrayList<RadioItem> getRecentRadioItems() {
        return getRadioStationsForMediaType(MediaListItem.RECENT);
    }

    public int getTrackPosition(Track track) {
        int trackPostion = -1;
        SQLiteDatabase database = UpodsApplication.getDatabaseManager().getReadableDatabase();
        String args[] = {track.getTitle()};
        Cursor cursor = database.rawQuery("SELECT position FROM tracks_positions WHERE track_name = ?", args);
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            trackPostion = cursor.getInt(cursor.getColumnIndex("position"));
        }
        return trackPostion;
    }

    public void addSubscribedMediaItem(MediaItem mediaItem) {
        if (!mediaItem.isSubscribed) {
            String mediaType = MediaListItem.TYPE_RADIO;
            if (mediaItem instanceof Podcast) {
                mediaType = MediaListItem.TYPE_PODCAST;
                Podcast podcast = ((Podcast) mediaItem);
                if (!mediaItem.isExistsInDb) {
                    podcast.save();
                }
                Feed.removeFeed(podcast.getFeedUrl());
                Feed.saveAsFeed(podcast.getFeedUrl(), podcast.getEpisodes(), true);
            } else if (mediaItem instanceof RadioItem) {
                if (!mediaItem.isExistsInDb) {
                    ((RadioItem) mediaItem).save();
                }
            }
            ContentValues values = new ContentValues();
            values.put("media_id", mediaItem.id);
            values.put("media_type", mediaType);
            values.put("list_type", MediaListItem.SUBSCRIBED);
            UpodsApplication.getDatabaseManager().getWritableDatabase().insert("media_list", null, values);
            mediaItem.isSubscribed = true;

            if (mediaItem instanceof Podcast && getSubscribedPodcasts().size() == 1) {
                UpodsApplication.setAlarmManagerTasks();
            }
        }
        notifyChanges(new ProfileUpdateEvent(MediaListItem.SUBSCRIBED, mediaItem, false));
    }

    public void addRecentMediaItem(MediaItem mediaItem) {
        if (mediaItem instanceof RadioItem) {
            if (!((RadioItem) mediaItem).isRecent) {
                if (!mediaItem.isExistsInDb) {
                    ((RadioItem) mediaItem).save();
                }
                ContentValues values = new ContentValues();
                values.put("media_id", mediaItem.id);
                values.put("media_type", MediaListItem.TYPE_RADIO);
                values.put("list_type", MediaListItem.RECENT);
                UpodsApplication.getDatabaseManager().getWritableDatabase().insert("media_list", null, values);
                ((RadioItem) mediaItem).isRecent = true;
                notifyChanges(new ProfileUpdateEvent(MediaListItem.RECENT, mediaItem, false));
            }
        }
    }

    public void addNewTrack(MediaItem mediaItem, Track track) {
        addTrack(mediaItem, track, MediaListItem.NEW);
    }

    public void addDownloadedTrack(MediaItem mediaItem, Track track) {
        addTrack(mediaItem, track, MediaListItem.DOWNLOADED);
    }

    public void saveTrackPosition(Track track, int position) {
        SQLiteDatabase database = UpodsApplication.getDatabaseManager().getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("track_name", track.getTitle());
        values.put("position", position);
        database.replace("tracks_positions", null, values);
    }

    public void removeNewTrack(MediaItem mediaItem, Track track) {
        removeTrack(mediaItem, track, MediaListItem.NEW);
    }

    public void removeDownloadedTrack(MediaItem mediaItem, Track track) {
        removeTrack(mediaItem, track, MediaListItem.DOWNLOADED);
    }

    public void removeRecentMediaItem(MediaItem mediaItem) {
        if (mediaItem instanceof RadioItem) {
            SQLiteDatabase database = UpodsApplication.getDatabaseManager().getWritableDatabase();
            String args[] = {String.valueOf(mediaItem.id), MediaListItem.TYPE_RADIO, MediaListItem.RECENT};
            database.delete("media_list", "media_id = ? AND media_type = ? AND list_type = ?", args);
            ((RadioItem) mediaItem).isRecent = false;

            //Remove media item from DB if it doesn't use
            if (!((RadioItem) mediaItem).isSubscribed) {
                String args2[] = {String.valueOf(mediaItem.id)};
                database.delete("radio_stations", "id = ?", args2);
            }
        }
        notifyChanges(new ProfileUpdateEvent(MediaListItem.SUBSCRIBED, mediaItem, true));
    }

    public void removeSubscribedMediaItem(MediaItem mediaItem) {
        SQLiteDatabase database = UpodsApplication.getDatabaseManager().getWritableDatabase();
        String type = MediaListItem.TYPE_RADIO;
        if (mediaItem instanceof Podcast) {
            type = MediaListItem.TYPE_PODCAST;
        }
        mediaItem.isSubscribed = false;
        String args[] = {String.valueOf(mediaItem.id), type, MediaListItem.SUBSCRIBED};
        database.delete("media_list", "media_id = ? AND media_type = ? AND list_type = ?", args);

        //Remove media item from DB if it doesn't use
        if (mediaItem instanceof RadioItem && !((RadioItem) mediaItem).isRecent) {
            String args2[] = {String.valueOf(mediaItem.id)};
            database.delete("radio_stations", "id = ?", args2);
        } else if (mediaItem instanceof Podcast && !((Podcast) mediaItem).isDownloaded && !((Podcast) mediaItem).hasNewEpisodes) {
            String args2[] = {String.valueOf(mediaItem.id)};
            database.delete("podcasts", "id = ?", args2);
        }

        notifyChanges(new ProfileUpdateEvent(MediaListItem.SUBSCRIBED, mediaItem, true));
    }

    public void notifyChanges(ProfileUpdateEvent updateEvent) {
        if (profileSavedCallback != null) {
            profileSavedCallback.operationFinished(updateEvent);
        }
    }

    private void initSubscribedPodcasts(JSONArray jSubscribedPodcasts) {
        try {
            SQLiteDatabase database = UpodsApplication.getDatabaseManager().getWritableDatabase();
            ArrayList<Podcast> subscribedPodcasts = new ArrayList<>();
            for (int i = 0; i < jSubscribedPodcasts.length(); i++) {
                Podcast podcast = new Podcast(jSubscribedPodcasts.getJSONObject(i));
                subscribedPodcasts.add(podcast);
            }

            if (subscribedPodcasts.isEmpty()) {
                return;
            }

            Podcast.syncWithDb(subscribedPodcasts);
            for (Podcast podcast : subscribedPodcasts) {
                if (!podcast.isSubscribed) {
                    addSubscribedMediaItem(podcast);
                }
            }
            ArrayList<String> ids = MediaItem.getIds(subscribedPodcasts);
            if (ids.size() == 0) {
                return;
            }
            ArrayList<String> args = new ArrayList<>();
            args.add(MediaListItem.TYPE_PODCAST);
            args.add(MediaListItem.SUBSCRIBED);
            args.addAll(ids);
            database.delete("media_list", "media_type = ? AND list_type = ? AND media_id not in (" + SQLdatabaseManager.makePlaceholders(ids.size()) + ")",
                    args.toArray(new String[args.size()]));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initRadioStations(JSONArray jStations, String listType) {
        try {
            SQLiteDatabase database = UpodsApplication.getDatabaseManager().getWritableDatabase();
            ArrayList<RadioItem> radioStations = new ArrayList<>();
            for (int i = 0; i < jStations.length(); i++) {
                RadioItem radioItem = new RadioItem(jStations.getJSONObject(i));
                radioStations.add(radioItem);
            }

            if (radioStations.isEmpty()) {
                return;
            }

            RadioItem.syncWithDb(radioStations);
            for (RadioItem radioItem : radioStations) {
                if (!radioItem.isSubscribed && listType.equals(MediaListItem.SUBSCRIBED)) {
                    addSubscribedMediaItem(radioItem);
                } else if (!radioItem.isRecent && listType.equals(MediaListItem.RECENT)) {
                    addRecentMediaItem(radioItem);
                }
            }
            ArrayList<String> ids = MediaItem.getIds(radioStations);

            if (ids.size() == 0) {
                return;
            }

            ArrayList<String> args = new ArrayList<>();
            args.add(MediaListItem.TYPE_RADIO);
            args.add(listType);
            args.addAll(ids);
            database.delete("media_list", "media_type = ? AND list_type = ? AND media_id not in (" + SQLdatabaseManager.makePlaceholders(ids.size()) + ")",
                    args.toArray(new String[args.size()]));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void readFromJson(JSONObject rootProfile) {
        try {
            //Read from JSON -> syncWithDB -> save all which not exists -> remove all which not in JSON
            // -> UI will be notified inside init... functions

            initSubscribedPodcasts(rootProfile.getJSONArray(JS_SUBSCRIBED_PODCASTS));
            initRadioStations(rootProfile.getJSONArray(JS_SUBSCRIBED_STATIONS), MediaListItem.SUBSCRIBED);
            initRadioStations(rootProfile.getJSONArray(JS_RECENT_STATIONS), MediaListItem.RECENT);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public JSONObject getAsJson() {
        JSONObject rootProfile = new JSONObject();
        try {
            rootProfile.put(JS_RECENT_STATIONS, RadioItem.toJsonArray(getRecentRadioItems()));
            rootProfile.put(JS_SUBSCRIBED_STATIONS, RadioItem.toJsonArray(getSubscribedRadioItems()));
            rootProfile.put(JS_SUBSCRIBED_PODCASTS, Podcast.toJsonArray(getSubscribedPodcasts()));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return rootProfile;
    }

}
