package com.example.googledriveapp;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_AUTHORIZATION = 1001;
    private static final int PICK_FILE_REQUEST = 1002;
    private static final String CHANNEL_ID = "download_channel";
    private GoogleSignInClient googleSignInClient;
    private GoogleSignInAccount googleSignInAccount;
    private Drive googleDriveService;
    private ProgressBar loadingProgressBar;
    private RecyclerView fileRecyclerView;
    private FileAdapter fileAdapter;
    private String selectedFileId;
    private Uri fileUriToUpload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        loadingProgressBar = findViewById(R.id.loading_progress_bar);
        fileRecyclerView = findViewById(R.id.file_recycler_view);

        Button logoutButton = findViewById(R.id.logout_button);
        Button uploadButton = findViewById(R.id.upload_button);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);
        googleSignInAccount = GoogleSignIn.getLastSignedInAccount(this);

        if (googleSignInAccount != null) {
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    this, Collections.singleton(DriveScopes.DRIVE_FILE));
            credential.setSelectedAccount(googleSignInAccount.getAccount());

            try {
                googleDriveService = new Drive.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        credential)
                        .setApplicationName("Google Drive App")
                        .build();

                listFiles();
            } catch (GeneralSecurityException | IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error initializing Google Drive service", Toast.LENGTH_LONG).show();
            }
        } else {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }

        logoutButton.setOnClickListener(v -> signOut());
        uploadButton.setOnClickListener(v -> openFilePicker());

        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Download Channel";
            String description = "Channel for file download notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void sendDownloadNotification(java.io.File file) {
        Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
        String mimeType = getMimeType(Uri.fromFile(file));

        Log.d(TAG, "File URI: " + fileUri.toString());
        Log.d(TAG, "MIME Type: " + mimeType);

        if (mimeType == null) {
            mimeType = "*/*"; // Default MIME type if unable to determine
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(fileUri, mimeType);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent chooser = Intent.createChooser(intent, "Open file with");
        chooser.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, chooser, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_download)
                .setContentTitle("File Downloaded")
                .setContentText("Tap to open file")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(1, builder.build());
        }
    }

    private String getMimeType(Uri uri) {
        String mimeType = null;
        ContentResolver contentResolver = getContentResolver();

        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            mimeType = contentResolver.getType(uri);
        } else if (ContentResolver.SCHEME_FILE.equals(uri.getScheme())) {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
        }

        if (mimeType == null) {
            mimeType = "*/*"; // Default MIME type if unable to determine
        }

        return mimeType;
    }

    private void signOut() {
        googleSignInClient.signOut()
                .addOnCompleteListener(this, task -> {
                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                    startActivity(intent);
                    finish();
                });
    }

    private void listFiles() {
        loadingProgressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                FileList result = googleDriveService.files().list()
                        .setPageSize(100)
                        .setFields("nextPageToken, files(id, name, mimeType)")
                        .execute();
                List<File> files = result.getFiles();

                runOnUiThread(() -> {
                    fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
                    fileAdapter = new FileAdapter(files, new FileAdapter.OnItemClickListener() {
                        @Override
                        public void onItemClick(File file) {
                            // Bu metodun içeriğini oluşturun
                            Uri fileUri = getFileUri(file);
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setDataAndType(fileUri, file.getMimeType());
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(intent);                        }

                        @Override
                        public void onDownloadClick(File file) {
                            downloadFile(file);
                        }

                        @Override
                        public void onEditClick(File file) {
                            editFile(file);
                        }

                        @Override
                        public void onDeleteClick(File file) {
                            deleteFile(file);
                        }
                    });
                    fileRecyclerView.setAdapter(fileAdapter);
                    loadingProgressBar.setVisibility(View.GONE);
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error retrieving files", Toast.LENGTH_LONG).show();
                    loadingProgressBar.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    private void downloadFile(File file) {
        new Thread(() -> {
            try {
                InputStream inputStream = googleDriveService.files().get(file.getId()).executeMediaAsInputStream();
                java.io.File tempFile = new java.io.File(getExternalFilesDir(null), file.getName());

                try (OutputStream outputStream = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                runOnUiThread(() -> {
                    sendDownloadNotification(tempFile);
                    Toast.makeText(MainActivity.this, "File downloaded to " + tempFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error downloading file", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void editFile(File file) {
        selectedFileId = file.getId();
        Intent editIntent = new Intent(Intent.ACTION_EDIT);
        editIntent.setDataAndType(getFileUri(file), file.getMimeType());
        editIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(editIntent, "Edit file with"), REQUEST_AUTHORIZATION);
    }

    private void deleteFile(File file) {
        new Thread(() -> {
            try {
                googleDriveService.files().delete(file.getId()).execute();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "File deleted", Toast.LENGTH_SHORT).show();
                    listFiles(); // Refresh file list
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error deleting file", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void uploadFile(Uri fileUri) {
        if (fileUri == null) return;

        String mimeType = getMimeType(fileUri);
        String fileName = getFileName(fileUri);

        try {
            InputStream inputStream = getContentResolver().openInputStream(fileUri);
            File fileMetadata = new File();
            fileMetadata.setName(fileName);

            InputStreamContent mediaContent = new InputStreamContent(mimeType, inputStream);

            new Thread(() -> {
                try {
                    File file = googleDriveService.files().create(fileMetadata, mediaContent)
                            .setFields("id")
                            .execute();
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "File uploaded: " + file.getName(), Toast.LENGTH_SHORT).show();
                        listFiles(); // Refresh file list
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error uploading file", Toast.LENGTH_LONG).show());
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Uri getFileUri(File file) {
        java.io.File tempFile = new java.io.File(getExternalFilesDir(null), file.getName());
        return FileProvider.getUriForFile(this, getPackageName() + ".provider", tempFile);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, PICK_FILE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                uploadFile(fileUri);
            }
        } else if (requestCode == REQUEST_AUTHORIZATION && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri editedFileUri = data.getData();
                if (editedFileUri != null && selectedFileId != null) {
                    deleteFileById(selectedFileId);
                    uploadFile(editedFileUri);
                    Toast.makeText(this, "Edit successful", Toast.LENGTH_SHORT).show(); // Success message

                }
            }
        }
    }

    private void deleteFileById(String fileId) {
        new Thread(() -> {
            try {
                googleDriveService.files().delete(fileId).execute();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Old file deleted", Toast.LENGTH_SHORT).show();
                    listFiles(); // Refresh file list
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error deleting old file", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}
