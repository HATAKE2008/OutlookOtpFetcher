package com.hatake.otpfetcher.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite database for Sessions + Accounts.
 *
 * <p>sessions:  id, title, created_at
 * <p>accounts:  id, session_id, email, password, refresh_token, client_id,
 *               otp, platform_name, is_used (default 0)
 */
public class AppDbHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "otp_fetcher.db";
    private static final int DB_VERSION = 1;

    public AppDbHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "created_at INTEGER NOT NULL" +
                ")");
        db.execSQL("CREATE TABLE accounts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "session_id INTEGER NOT NULL," +
                "email TEXT NOT NULL," +
                "password TEXT," +
                "refresh_token TEXT," +
                "client_id TEXT," +
                "otp TEXT," +
                "platform_name TEXT," +
                "is_used INTEGER NOT NULL DEFAULT 0" +
                ")");
        db.execSQL("CREATE INDEX idx_accounts_session ON accounts(session_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS accounts");
        db.execSQL("DROP TABLE IF EXISTS sessions");
        onCreate(db);
    }

    // ---------- Sessions ----------

    public long insertSession(String title, long createdAt) {
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("created_at", createdAt);
        return getWritableDatabase().insert("sessions", null, cv);
    }

    public List<Session> getAllSessions() {
        List<Session> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, title, created_at FROM sessions ORDER BY id DESC", null)) {
            while (c.moveToNext()) {
                list.add(new Session(c.getLong(0), c.getString(1), c.getLong(2)));
            }
        }
        return list;
    }

    public int getAccountCount(long sessionId) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM accounts WHERE session_id = ?",
                new String[]{String.valueOf(sessionId)})) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    // ---------- Accounts ----------

    public long insertAccount(long sessionId, String email, String password,
                              String refreshToken, String clientId) {
        ContentValues cv = new ContentValues();
        cv.put("session_id", sessionId);
        cv.put("email", email);
        cv.put("password", password);
        cv.put("refresh_token", refreshToken);
        cv.put("client_id", clientId);
        cv.put("otp", "");
        cv.put("platform_name", "");
        cv.put("is_used", 0);
        return getWritableDatabase().insert("accounts", null, cv);
    }

    public List<Account> getAccountsForSession(long sessionId) {
        List<Account> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, session_id, email, password, refresh_token, client_id, " +
                        "otp, platform_name, is_used FROM accounts " +
                        "WHERE session_id = ? ORDER BY id ASC",
                new String[]{String.valueOf(sessionId)})) {
            while (c.moveToNext()) {
                list.add(new Account(
                        c.getLong(0), c.getLong(1), c.getString(2), c.getString(3),
                        c.getString(4), c.getString(5), c.getString(6), c.getString(7),
                        c.getInt(8) == 1));
            }
        }
        return list;
    }

    public void updateOtpAndPlatform(long accountId, String otp, String platform) {
        ContentValues cv = new ContentValues();
        cv.put("otp", otp);
        cv.put("platform_name", platform);
        getWritableDatabase().update("accounts", cv, "id = ?",
                new String[]{String.valueOf(accountId)});
    }

    public void updateUsed(long accountId, boolean used) {
        ContentValues cv = new ContentValues();
        cv.put("is_used", used ? 1 : 0);
        getWritableDatabase().update("accounts", cv, "id = ?",
                new String[]{String.valueOf(accountId)});
    }
}
