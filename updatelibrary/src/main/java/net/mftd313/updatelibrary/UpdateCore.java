package net.mftd313.updatelibrary;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.FileProvider;

import java.io.File;

import static android.content.Context.DOWNLOAD_SERVICE;

final class UpdateCore {

    static void beginInstall(Context context, Uri uri) {
        File file = new File(context.getExternalFilesDir(null), uri.getLastPathSegment());
        if (!file.exists() || !UpdateCore.isDownloadSuccess(context, uri)) {
            return;
        }
        if (UpdateLibrary.getInstallStartedListener() != null) {
            UpdateLibrary.getInstallStartedListener().onInstallStarted(context, uri);
        }
        File apkFile = new File(context.getExternalFilesDir(null), uri.getLastPathSegment());
        Uri apkUri;
        Intent install;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            apkUri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".net.mftd313.updatelibrary.fileprovider", apkFile);
            install = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            install.setData(apkUri);
            install.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            apkUri = Uri.fromFile(apkFile);
            install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(install);
        context.stopService(new Intent(context, UpdateInstallService.class));
    }

    static void beginDownload(Context context, Uri uri) {
        if (UpdateLibrary.getDownloadStartedListener() != null) {
            UpdateLibrary.getDownloadStartedListener().onDownloadStarted(context, uri);
        }
        File file = new File(context.getExternalFilesDir(null), uri.getLastPathSegment());
        if (file.exists()) {
            if (isDownloadSuccess(context, uri)) {
                if (UpdateLibrary.getReadyToInstallListener() != null) {
                    UpdateLibrary.getReadyToInstallListener().onReadyToInstall(context, uri);
                }
                return;
            }
            else {
                file.delete();
            }
        }
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .setTitle(UpdateRepository.getInstance(context).getDownloadingNotificationTitle())
                .setDescription(UpdateRepository.getInstance(context).getDownloadingNotificationText())
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(file))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true);
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);
        long downloadID = downloadManager.enqueue(request);
        UpdateRepository.getInstance(context).setLastDownloadId(downloadID);

        final ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                int progress = 0;
                long totalBytes = 0l;
                boolean isDownloadFinished = false;
                while (!isDownloadFinished) {
                    Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(downloadID));
                    if (cursor.moveToFirst()) {
                        int downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                        switch (downloadStatus) {
                            case DownloadManager.STATUS_RUNNING:
                                totalBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                                if (totalBytes > 0) {
                                    long downloadedBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                                    System.out.println("so far:"+downloadedBytes);
                                    progress = (int) (downloadedBytes * 100 / totalBytes);
                                }
                                break;
                            case DownloadManager.STATUS_SUCCESSFUL:
                                progress = 100;
                                isDownloadFinished = true;
                                break;
                            case DownloadManager.STATUS_PAUSED:

                            case DownloadManager.STATUS_PENDING:
                                break;
                            case DownloadManager.STATUS_FAILED:
                                isDownloadFinished = true;
                                break;
                        }
                    }
                }
            }
        });


    }

    static boolean isDownloadRunning(Context context, Uri uri) {
        boolean isDownloadRunning = false;

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);

        Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_RUNNING));
        String downingURI;
        while (cursor.moveToNext()) {
            downingURI = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI));
            if (downingURI.equalsIgnoreCase(uri.toString())) {
                isDownloadRunning = true;
                break;
            }
        }
        cursor.close();
        return isDownloadRunning;
    }

    static boolean isDownloadPending(Context context, Uri uri) {
        boolean isPending = false;

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);

        Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_PENDING | DownloadManager.STATUS_PAUSED));
        String downingURI;
        while (cursor.moveToNext()) {
            downingURI = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI));
            if (downingURI.equalsIgnoreCase(uri.toString())) {
                isPending = true;
                break;
            }
        }
        cursor.close();
        return isPending;
    }

    static boolean isDownloadSuccess(Context context, Uri uri) {
        boolean isDownloaded = false;

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);

        Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL));
        String downingURI;
        while (cursor.moveToNext()) {
            downingURI = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_URI));
            if (downingURI.equalsIgnoreCase(uri.toString())) {
                isDownloaded = true;
                break;
            }
        }
        cursor.close();
        return isDownloaded;
    }
}
