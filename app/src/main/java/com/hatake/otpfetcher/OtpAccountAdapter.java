package com.hatake.otpfetcher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * RecyclerView adapter for OTP accounts.
 *
 * <p>Row: clickable email (copy + toast), status TextView, Fetch OTP button.
 * Fetch uses OkHttp on a background ExecutorService thread, UI updates via main Looper.
 */
public class OtpAccountAdapter extends RecyclerView.Adapter<OtpAccountAdapter.OtpViewHolder> {

    private final List<OtpAccount> accounts = new ArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Pattern otpPattern = Pattern.compile("\\b\\d{5,8}\\b");

    public void setAccounts(List<OtpAccount> newAccounts) {
        accounts.clear();
        accounts.addAll(newAccounts);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OtpViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_otp_account, parent, false);
        return new OtpViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OtpViewHolder holder, int position) {
        OtpAccount account = accounts.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvEmail.setText(account.getEmail());
        holder.tvStatus.setText(account.getStatus());

        boolean loading = "Loading...".equals(account.getStatus());
        holder.btnFetch.setEnabled(!loading);

        // Tap email -> copy + toast.
        holder.tvEmail.setOnClickListener(v -> {
            copyToClipboard(ctx, account.getEmail());
            Toast.makeText(ctx, "Email copied", Toast.LENGTH_SHORT).show();
        });

        holder.btnFetch.setOnClickListener(v -> {
            account.setStatus("Loading...");
            holder.tvStatus.setText("Loading...");
            holder.btnFetch.setEnabled(false);
            fetchOtpFromMicrosoft(ctx, account, holder);
        });
    }

    @Override
    public int getItemCount() {
        return accounts.size();
    }

    // Microsoft identity endpoints + mail APIs (exact fallback chain):
    // 1. Token primary: consumers v2.0 endpoint with scope "offline_access Mail.Read".
    // 2. Token fallback: legacy login.live.com endpoint (no scope).
    // 3. Mail primary: Microsoft Graph; 4. Mail fallback: Outlook REST API.
    private static final String SCOPE = "offline_access Mail.Read";
    private static final String TOKEN_V2 = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String TOKEN_LIVE = "https://login.live.com/oauth20_token.srf";
    private static final String GRAPH_URL = "https://graph.microsoft.com/v1.0/me/mailFolders/Inbox/messages?$top=1&$select=subject,bodyPreview";
    private static final String OUTLOOK_URL = "https://outlook.office.com/api/v2.0/me/mailfolders/inbox/messages?$top=1&$select=Subject,BodyPreview";

    private void fetchOtpFromMicrosoft(Context ctx, OtpAccount account, OtpViewHolder holder) {
        final Context appCtx = ctx.getApplicationContext();
        final int adapterPos = holder.getBindingAdapterPosition();

        executor.execute(() -> {
            try {
                // 1) Exchange refresh_token for access_token.
                // Try modern consumers endpoint (with Graph Mail scope) first,
                // fall back to legacy live.com endpoint.
                String accessToken = null;
                Exception tokenErr = null;
                try {
                    accessToken = refreshAccessToken(TOKEN_V2, account, true);
                } catch (Exception e) {
                    tokenErr = e;
                }
                if (accessToken == null) {
                    try {
                        accessToken = refreshAccessToken(TOKEN_LIVE, account, false);
                    } catch (Exception e) {
                        tokenErr = e;
                    }
                }
                if (accessToken == null) {
                    throw new Exception("Token refresh failed: "
                            + (tokenErr != null ? tokenErr.getMessage() : "unknown"));
                }

                // 2) Fetch latest inbox message. Graph first, Outlook REST fallback
                // (live.com tokens are rejected by Graph with 401).
                String haystack = null;
                Exception graphErr = null;
                try {
                    haystack = fetchGraph(accessToken);
                } catch (Exception e) {
                    graphErr = e;
                }
                if (haystack == null) {
                    try {
                        haystack = fetchOutlookRest(accessToken);
                    } catch (Exception e) {
                        throw new Exception("Mail fetch failed (" + graphErr.getMessage()
                                + " / " + e.getMessage() + ")");
                    }
                }

                // 3) Extract 5-8 digit OTP.
                Matcher m = otpPattern.matcher(haystack);
                final String result;
                if (m.find()) {
                    result = m.group();
                } else {
                    result = "No OTP found";
                }

                mainHandler.post(() -> {
                    account.setStatus(result);
                    copyToClipboard(appCtx, result);
                    Toast.makeText(appCtx, "OTP: " + result, Toast.LENGTH_SHORT).show();
                    if (adapterPos != RecyclerView.NO_POSITION) {
                        notifyItemChanged(adapterPos);
                    } else {
                        notifyDataSetChanged();
                    }
                });
            } catch (Exception e) {
                final String err = "Error: " + e.getMessage();
                mainHandler.post(() -> {
                    account.setStatus(err);
                    Toast.makeText(appCtx, err, Toast.LENGTH_SHORT).show();
                    if (adapterPos != RecyclerView.NO_POSITION) {
                        notifyItemChanged(adapterPos);
                    } else {
                        notifyDataSetChanged();
                    }
                });
            }
        });
    }

    private String refreshAccessToken(String url, OtpAccount account, boolean withScope)
            throws Exception {
        FormBody.Builder builder = new FormBody.Builder()
                .add("client_id", account.getClientId())
                .add("grant_type", "refresh_token")
                .add("refresh_token", account.getRefreshToken());
        if (withScope) {
            builder.add("scope", SCOPE);
        }
        RequestBody tokenBody = builder.build();
        Request tokenRequest = new Request.Builder()
                .url(url)
                .post(tokenBody)
                .build();
        try (Response tokenResp = httpClient.newCall(tokenRequest).execute()) {
            String tokenJson = tokenResp.body() != null ? tokenResp.body().string() : "";
            if (!tokenResp.isSuccessful()) {
                throw new Exception("Token HTTP " + tokenResp.code() + ": " + tokenJson);
            }
            JSONObject obj = new JSONObject(tokenJson);
            if (!obj.has("access_token")) {
                throw new Exception("No access_token: " + tokenJson);
            }
            return obj.getString("access_token");
        }
    }

    private String fetchGraph(String accessToken) throws Exception {
        Request msgRequest = new Request.Builder()
                .url(GRAPH_URL)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();
        try (Response msgResp = httpClient.newCall(msgRequest).execute()) {
            String msgJson = msgResp.body() != null ? msgResp.body().string() : "";
            if (!msgResp.isSuccessful()) {
                throw new Exception("Graph HTTP " + msgResp.code() + " " + msgJson);
            }
            JSONObject root = new JSONObject(msgJson);
            JSONArray values = root.optJSONArray("value");
            if (values == null || values.length() == 0) {
                throw new Exception("Inbox empty");
            }
            JSONObject latest = values.getJSONObject(0);
            String subject = latest.optString("subject", "");
            String preview = latest.optString("bodyPreview", "");
            return subject + "\n" + preview;
        }
    }

    private String fetchOutlookRest(String accessToken) throws Exception {
        Request msgRequest = new Request.Builder()
                .url(OUTLOOK_URL)
                .addHeader("Authorization", "Bearer " + accessToken)
                .get()
                .build();
        try (Response msgResp = httpClient.newCall(msgRequest).execute()) {
            String msgJson = msgResp.body() != null ? msgResp.body().string() : "";
            if (!msgResp.isSuccessful()) {
                throw new Exception("Outlook HTTP " + msgResp.code() + " " + msgJson);
            }
            JSONObject root = new JSONObject(msgJson);
            JSONArray values = root.optJSONArray("value");
            if (values == null || values.length() == 0) {
                throw new Exception("Inbox empty");
            }
            JSONObject latest = values.getJSONObject(0);
            String subject = latest.optString("Subject", "");
            String preview = latest.optString("BodyPreview", "");
            return subject + "\n" + preview;
        }
    }

    private void copyToClipboard(Context ctx, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("otp", text));
        }
    }

    static class OtpViewHolder extends RecyclerView.ViewHolder {
        final TextView tvEmail;
        final TextView tvStatus;
        final Button btnFetch;

        OtpViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnFetch = itemView.findViewById(R.id.btnFetch);
        }
    }
}
