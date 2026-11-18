package com.example.myfit.flutter;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.os.storage.StorageManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** No target-app or test-library ABI dependency: inspects the exact distributable APK. */
public class PackagingInstrumentation extends Instrumentation {
    private boolean storageOnly;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        storageOnly = arguments != null && "true".equals(arguments.getString("storageOnly"));
        start();
    }
    private void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            StorageStats stats = ((StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE))
                .queryStatsForPackage(StorageManager.UUID_DEFAULT, context.getPackageName(), Process.myUserHandle());
            result.putLong("appBytes", stats.getAppBytes());
            result.putLong("dataBytes", stats.getDataBytes());
            result.putLong("cacheBytes", stats.getCacheBytes());
            if (!storageOnly) {
                String[] names = context.getAssets().list("catalog/videos");
                require(names != null, "Missing videos directory");
                Arrays.sort(names);
                int webps = 0;
                for (String name : names) {
                    require(!name.endsWith(".gif"), "Archived GIF in release APK");
                    if (!name.endsWith(".webp")) continue;
                    if (webps % 200 == 0) {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        try (InputStream input = context.getAssets().open("catalog/videos/" + name)) {
                            byte[] buffer = new byte[8192]; int count;
                            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                        }
                        Drawable drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes.toByteArray())));
                        require(drawable instanceof AnimatedImageDrawable, "Sample does not animate: " + name);
                        runOnMainSync(() -> {
                            AnimatedImageDrawable animated = (AnimatedImageDrawable) drawable;
                            animated.start(); require(animated.isRunning(), "Animation did not start"); animated.stop();
                        });
                    }
                    webps++;
                }
                require(webps == 1324, "Wrong WebP count");
                int databases = 0;
                for (String name : context.getAssets().list("catalog")) if (name.endsWith(".sqlite.bin")) {
                    require(name.equals("gymnota-catalog-v5.sqlite.bin"), "Archived database in release APK"); databases++;
                }
                require(databases == 1, "Wrong packaged catalogue count");
                File folder = new File(context.getNoBackupFilesDir(), "exercise_catalog");
                File database = new File(folder, "gymnota-catalog-v5.sqlite");
                require(database.isFile(), "Open Exercises before running this inspector");
                require(database.length() < 10L*1024*1024, "Catalogue not compacted");
                for (File old : folder.listFiles()) require(!old.getName().matches("gymnota-catalog-v[0-4]\\.sqlite"), "Old catalogue remains");
                try (SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                     Cursor rows = db.rawQuery("SELECT COUNT(*) FROM catalog_translation", null)) {
                    require(rows.moveToFirst() && rows.getInt(0) == 15888, "Translations missing");
                }
                result.putLong("catalogBytes", database.length());
                result.putInt("webpCount", webps);
            }
            result.putString("stream", "OK: release package and installed storage verified\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
