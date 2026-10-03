package com.hatake.otpfetcher.db;

/**
 * An account belonging to a Session.
 * Fields: email, password, refresh_token, client_id, otp, platform_name, is_used.
 */
public class Account {
    private final long id;
    private final long sessionId;
    private final String email;
    private final String password;
    private final String refreshToken;
    private final String clientId;
    private String otp;
    private String platformName;
    private boolean isUsed;
    private String status = "";

    public Account(long id, long sessionId, String email, String password,
                   String refreshToken, String clientId, String otp,
                   String platformName, boolean isUsed) {
        this.id = id;
        this.sessionId = sessionId;
        this.email = email;
        this.password = password;
        this.refreshToken = refreshToken;
        this.clientId = clientId;
        this.otp = otp == null ? "" : otp;
        this.platformName = platformName == null ? "" : platformName;
        this.isUsed = isUsed;
    }

    public long getId() {
        return id;
    }

    public long getSessionId() {
        return sessionId;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getClientId() {
        return clientId;
    }

    public String getOtp() {
        return otp;
    }

    public String getPlatformName() {
        return platformName;
    }

    public boolean isUsed() {
        return isUsed;
    }

    public String getStatus() {
        return status;
    }

    public void setOtp(String otp) {
        this.otp = otp == null ? "" : otp;
    }

    public void setPlatformName(String platformName) {
        this.platformName = platformName == null ? "" : platformName;
    }

    public void setUsed(boolean used) {
        isUsed = used;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
