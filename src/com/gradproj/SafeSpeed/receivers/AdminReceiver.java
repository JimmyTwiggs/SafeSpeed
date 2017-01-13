package com.gradproj.SafeSpeed.receivers;

import com.gradproj.SafeSpeed.MyApp;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class AdminReceiver extends DeviceAdminReceiver {
	public static final String PREFS_NAME = "SafeSpeedPrefs";
	private static SharedPreferences settings = MyApp.getAppContext().getSharedPreferences(PREFS_NAME, 0);
	private static Editor editor = settings.edit();
	
    public static final String ACTION_DISABLED = "device_admin_action_disabled";
    public static final String ACTION_ENABLED = "device_admin_action_enabled";

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        LocalBroadcastManager.getInstance(context).sendBroadcast(
            new Intent(ACTION_DISABLED));
        editor.putBoolean("isAdmin", false);
		editor.commit();
    }
    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.i("SafeSpeed", "AdminReceiver.OnEnabled()");
        LocalBroadcastManager.getInstance(context).sendBroadcast(
            new Intent(ACTION_ENABLED));
        editor.putBoolean("isAdmin", true);
		editor.commit();
    }
}