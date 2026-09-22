package dev.habitdock;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.os.Process;
import android.os.UserManager;
import java.util.*;

/**
 * Only foreground package names and timestamps are retained, at most 60 days.
 */
final class UsageStore extends SQLiteOpenHelper {
    private final Context context;
    UsageStore(Context c) {
        super(c, "habits.db", null, 1);
        context = c.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE launches (pkg TEXT NOT NULL, stamp INTEGER NOT NULL, PRIMARY KEY(pkg,stamp))");
        db.execSQL("CREATE INDEX launches_stamp ON launches(stamp)");
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    static boolean permitted(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    private long value(SQLiteDatabase db, String key, long fallback) {
        try (Cursor cursor = db.rawQuery("SELECT value FROM meta WHERE key=?", new String[]{key})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : fallback;
        }
    }

    private void put(SQLiteDatabase db, String key, long value) {
        ContentValues row = new ContentValues();
        row.put("key", key);
        row.put("value", value);
        db.insertWithOnConflict("meta", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void sync(long now) {
        if (!permitted(context))
            return;
        UserManager user = context.getSystemService(UserManager.class);
        if (user != null && !user.isUserUnlocked())
            return;
        SQLiteDatabase db = getWritableDatabase();
        long floor = Math.max(now - 60 * Predictor.DAY, value(db, "reset", 0));
        long from = Math.max(floor, Math.min(now, value(db, "cursor", floor)) - 300_000);
        UsageStatsManager manager = context.getSystemService(UsageStatsManager.class);
        Set<String> eligible = Repository.eligible(context, Repository.apps(context).keySet());
        if (eligible.isEmpty())
            return;
        UsageEvents events = manager.queryEvents(from, now);
        if (events == null)
            return;
        db.beginTransaction();
        try {
            UsageEvents.Event event = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                // MOVE_TO_FOREGROUND (26-28) and ACTIVITY_RESUMED (29+) share value 1.
                if (event.getEventType() != 1 || event.getPackageName() == null)
                    continue;
                if (!eligible.contains(event.getPackageName()))
                    continue;
                if (event.getTimeStamp() < floor || event.getTimeStamp() > now)
                    continue;
                ContentValues row = new ContentValues();
                row.put("pkg", event.getPackageName());
                row.put("stamp", event.getTimeStamp());
                db.insertWithOnConflict("launches", null, row, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.delete("launches", "stamp<? OR stamp>?", new String[]{Long.toString(floor), Long.toString(now)});
            put(db, "cursor", now);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    List<Predictor.Launch> read(long now) {
        List<Predictor.Launch> events = new ArrayList<>();
        // Collapse rapid in-app resumes; these are estimates, not exact launcher tap
        // counts.
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT pkg,stamp FROM launches WHERE stamp>=? AND stamp<=? ORDER BY stamp,pkg",
                new String[]{Long.toString(now - 60 * Predictor.DAY), Long.toString(now)})) {
            String previous = "";
            long previousStamp = 0;
            while (cursor.moveToNext()) {
                String pkg = cursor.getString(0);
                long stamp = cursor.getLong(1);
                // Collapse activity-to-activity resumes within the same app.
                if (!pkg.equals(previous) || stamp - previousStamp > 30_000)
                    events.add(new Predictor.Launch(pkg, stamp));
                previous = pkg;
                previousStamp = stamp;
            }
        }
        return events;
    }

    void reset(long now) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("launches", null, null);
            db.delete("meta", null, null);
            put(db, "reset", now);
            put(db, "cursor", now);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    void enforcePrivacy(Set<String> eligible) {
        SQLiteDatabase db = getWritableDatabase();
        List<String> remove = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT DISTINCT pkg FROM launches", null)) {
            while (cursor.moveToNext())
                if (!eligible.contains(cursor.getString(0)))
                    remove.add(cursor.getString(0));
        }
        db.beginTransaction();
        try {
            for (String pkg : remove)
                db.delete("launches", "pkg=?", new String[]{pkg});
            // Reconsider history after removing privacy exclusions, respecting a reset
            // cutoff.
            db.delete("meta", "key=?", new String[]{"cursor"});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
