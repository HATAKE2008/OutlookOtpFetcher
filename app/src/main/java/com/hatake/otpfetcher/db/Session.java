package com.hatake.otpfetcher.db;

/**
 * A saved session: a bulk import of accounts grouped under one title,
 * e.g. "Hotmails - 21-02-2026 14:30".
 */
public class Session {
    private final long id;
    private final String title;
    private final long createdAt;
    private int accountCount;

    public Session(long id, String title, long createdAt) {
        this.id = id;
        this.title = title;
        this.createdAt = createdAt;
        this.accountCount = 0;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getAccountCount() {
        return accountCount;
    }

    public void setAccountCount(int accountCount) {
        this.accountCount = accountCount;
    }
}
