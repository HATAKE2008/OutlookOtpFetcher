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
            fetchOtp(ctx, account, holder);
        });
    }

    @Override
    public int getItemCount() {
        return accounts.size();
    }

    private void fetchOtp(Context ctx, OtpAccount account, OtpViewHolder holder) {
        final Context appCtx = ctx.getApplicationContext();
        final int adapterPos = holder.getBindingAdapterPosition();

        executor.execute(() -> {
            try {
                // 1) Exchange refresh_token for access_token.
                RequestBody tokenBody = new FormBody.Builder()
                        .add("client_id", account.getClientId())
                        .add("grant_type", "refresh_token")
                        .add("refresh_token", account.getRefreshToken())
                        .build();

                Request tokenRequest = new Request.Builder()
                        .url("https://login.live.com/oauth20_token.srf")
                        .post(tokenBody)
                        .build();

                String accessToken;
                try (Response tokenResp = httpClient.newCall(tokenRequest).execute()) {
                    if (!tokenResp.isSuccessful() || tokenResp.body() == null) {
                        throw new Exception("Token HTTP " + tokenResp.code());
                    }
                    String tokenJson = tokenResp.body().string();
                    JSONObject obj = new JSONObject(tokenJson);
                    if (!obj.has("access_token")) {
                        throw new Exception("No access_token: " + tokenJson);
                    }
                    accessToken = obj.getString("access_token");
                }

                // 2) Fetch latest inbox message subject + preview.
                Request msgRequest = new Request.Builder()
                        .url("https://graph.microsoft.com/v1.0/me/mailFolders/Inbox/messages?$top=1&$select=subject,bodyPreview")
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .get()
                        .build();

                String haystack;
                try (Response msgResp = httpClient.newCall(msgRequest).execute()) {
                    if (!msgResp.isSuccessful() || msgResp.body() == null) {
                        throw new Exception("Graph HTTP " + msgResp.code());
                    }
                    String msgJson = msgResp.body().string();
                    JSONObject root = new JSONObject(msgJson);
                    JSONArray values = root.optJSONArray("value");
                    if (values == null || values.length() == 0) {
                        throw new Exception("Inbox empty");
                    }
                    JSONObject latest = values.getJSONObject(0);
                    String subject = latest.optString("subject", "");
                    String preview = latest.optString("bodyPreview", "");
                    haystack = subject + "\n" + preview;
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
