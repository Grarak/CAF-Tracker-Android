/*
 * Copyright (C) 2016 Willi Ye
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.grarak.cafntracker;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Created by willi on 01.09.16.
 */

public class MessagingServer extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String name = remoteMessage.getData().get("name");
        String title = remoteMessage.getData().get("title");
        String message = remoteMessage.getData().get("message");
        String link = remoteMessage.getData().get("link");

        if (!PreferenceManager.getDefaultSharedPreferences(this).getBoolean(name, false)) {
            return;
        }

        String filter;
        if (!(filter = PreferenceManager.getDefaultSharedPreferences(this).getString(name + "_filter", "")).isEmpty()) {
            if (!title.contains(filter)) {
                return;
            }
        }

        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(link));

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, i, 0);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(name + ": " + title)
                        .setContentText(message)
                        .setContentIntent(pendingIntent)
                        .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);

        int id = PreferenceManager.getDefaultSharedPreferences(this).getInt("notification", 0);

        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mNotifyMgr.notify(id, builder.build());
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt("notification", id + 1).apply();

    }

}
