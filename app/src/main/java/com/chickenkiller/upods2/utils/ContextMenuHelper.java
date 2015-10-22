package com.chickenkiller.upods2.utils;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import com.chickenkiller.upods2.R;
import com.chickenkiller.upods2.controllers.ProfileManager;
import com.chickenkiller.upods2.interfaces.IFragmentsManager;
import com.chickenkiller.upods2.interfaces.IPlayableMediaItem;
import com.chickenkiller.upods2.interfaces.OnActionFinished;
import com.chickenkiller.upods2.models.Podcast;
import com.chickenkiller.upods2.view.controller.DialogFragmentConfarmation;
import com.chickenkiller.upods2.view.controller.DialogFragmentMessage;

import java.io.File;

/**
 * Created by alonzilberman on 10/22/15.
 * Put all logic for context menu clicks here
 */
public class ContextMenuHelper {

    public static void showAboutPodcastDialog(Podcast podcast, IFragmentsManager fragmentsManager) {
        DialogFragmentMessage dialogFragmentMessage = new DialogFragmentMessage();
        dialogFragmentMessage.setTitle(podcast.getName());
        dialogFragmentMessage.setMessage(podcast.getDescription());
        fragmentsManager.showDialogFragment(dialogFragmentMessage);
    }

    public static void showPodcastInFolder(IPlayableMediaItem mediaItemData, Activity activity) {
        String path = ProfileManager.getInstance().getDownloadedMediaItemPath(mediaItemData);
        Uri selectedUri = Uri.parse(path);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(selectedUri, "*/*");
        activity.startActivity(intent);
    }

    public static void removeAllDonwloadedEpisods(Activity activity, final Podcast podcast, final OnActionFinished contextItemSelected) {
        DialogFragmentConfarmation dialogFragmentConfarmation = new DialogFragmentConfarmation();
        dialogFragmentConfarmation.setTitle(podcast.getName());
        dialogFragmentConfarmation.setMessage(activity.getString(R.string.remove_podcast_conformation));
        dialogFragmentConfarmation.setPositiveClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String path = ProfileManager.getInstance().getDownloadedMediaItemPath(podcast);
                GlobalUtils.deleteDirectory(new File(path));
                ProfileManager.getInstance().removeDownloadedMediaItem(podcast);
                contextItemSelected.onActionFinished();
            }
        });
        ((IFragmentsManager) activity).showDialogFragment(dialogFragmentConfarmation);
    }

}
