package me.rcoh.terminalwatcher;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by russell on 1/22/15.
 */
public class TerminalMonitorService extends IntentService {
    private WebSocketClient mWebSocketClient;
    final int START = 0;
    final String host = "130.211.141.47";
    public TerminalMonitorService() {
        super("TerminalMonitorService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.i("Monit", "handling intent");
        connectWebSocket();
    }

    private void connectWebSocket() {
        Process p1 = null;
        try {
            p1 = Runtime.getRuntime().exec("ping -c 1 " + host);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int returnVal = 0;
        try {
            returnVal = p1.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.i("PING", Integer.toString(returnVal));
        boolean reachable = (returnVal==0);
        URI uri;
        try {
            uri = new URI("ws://" + host + "/ws");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        mWebSocketClient = new WebSocketClient(uri, new Draft_17()) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.i("Websocket", "Opened");
                mWebSocketClient.send("Hello from " + Build.MANUFACTURER + " " + Build.MODEL);
            }

            @Override
            public void onMessage(String s) {
                final String message = s;
                Log.i("Websocket", "Got message" + message);
                try {
                    JSONObject jsonObject = new JSONObject(message);
                    int mode = jsonObject.optInt("Mode", -1);
                    String command = jsonObject.optString("Command", "Command not specified");
                    if (mode != -1) {
                        sendNotification(mode == 0, command, jsonObject.optInt("Status", -1));
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }


            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i("Websocket", "Closed " + s);
            }

            @Override
            public void onError(Exception e) {
                Log.i("Websocket", "Error " + e.getMessage());
            }
        };
        Log.i("Websocket", "connecting");
        mWebSocketClient.connect();
        //mWebSocketClient.send("test test");
    }

    private void sendNotification(boolean isStart, String command, int status) {
        Log.i("watch", "in this func");
        String mode = isStart ? "Start" : "Complete";
        int icon = status == 0 ? R.drawable.ic_stat_name : R.drawable.ic_stat_name2;
        if (status != 0) {
            mode = "FAILED";
        }

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(icon)
                        .setContentTitle("Terminal Watcher - " + mode)
                        .setContentText(command);

        Intent resultIntent = new Intent(this, TerminalWatch.class);

        // Because clicking the notification opens a new ("special") activity, there's
        // no need to create an artificial back stack.
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );


        // Sets an ID for the notification
        int mNotificationId = 001;
        // Gets an instance of the NotificationManager service
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        // Builds the notification and issues it.
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (!isStart) {
            v.vibrate(500);
        }
        Log.i("watch", "done");
    }
}
