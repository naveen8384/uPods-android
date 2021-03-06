package com.chickenkiller.upods2.interfaces;

import org.json.JSONObject;

/**
 * Created by Alon Zilberman on 7/11/15.
 * Handles http responses when response is json
 */
public interface IRequestCallback {

    void onRequestSuccessed(JSONObject jResponse);

    void onRequestFailed();
}
