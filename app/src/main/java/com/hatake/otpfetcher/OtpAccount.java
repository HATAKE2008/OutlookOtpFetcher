package com.hatake.otpfetcher;

/**
 * Model for one Outlook/Hotmail account line:
 * email|password|refresh_token|client_id
 */
public class OtpAccount {
    private final String email;
    private final String password;
    private final String refreshToken;
    private final String clientId;
    private String status = "Ready";

    public OtpAccount(String email, String password, String refreshToken, String clientId) {
        this.email = email;
        this.password = password;
        this.refreshToken = refreshToken;
        this.clientId = clientId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
