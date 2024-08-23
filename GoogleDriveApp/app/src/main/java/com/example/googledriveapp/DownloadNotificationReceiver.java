package com.example.googledriveapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public class DownloadNotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String filePath = intent.getStringExtra("file_path");
        Uri fileUri = Uri.parse(filePath);
        Intent viewIntent = new Intent(Intent.ACTION_VIEW);
        viewIntent.setDataAndType(fileUri, context.getContentResolver().getType(fileUri));
        viewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Check if there's an app that can handle the intent
        if (viewIntent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(viewIntent);
        } else {
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_LONG).show();
        }
    }
}
