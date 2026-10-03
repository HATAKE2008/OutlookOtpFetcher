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

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * RecyclerView adapter for OTP accounts.
 *
 * <p>Row: clickable email (copy + toast), status TextView, Fetch OTP button.
 * Fetch POSTs account details to the third-party extractor API on a background
 * ExecutorService thread, UI updates via main Looper.
 */
public class OtpAccountAdapter extends RecyclerView.Adapter<OtpAccountAdapter.OtpViewHolder> {

    private static final String API_URL = "https://tools.dongvanfb.net/api/get_messages_oauth2";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final List<OtpAccount> accounts = new ArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
                // Build JSON payload: email / pass / refresh_token / client_id.
                JSONObject payload = new JSONObject();
                payload.put("email", account.getEmail());
                payload.put("pass", account.getPassword());
                payload.put("refresh_token", account.getRefreshToken());
                payload.put("client_id", account.getClientId());

                RequestBody body = RequestBody.create(payload.toString(), JSON);
                Request request = new Request.Builder()
                        .url(API_URL)
                        .addHeader("Content-Type", "application/json")
                        .post(body)
                        .build();

                // Parse response: { email, status, code, messages }.
                final String otp;
                try (Response resp = httpClient.newCall(request).execute()) {
                    String respBody = resp.body() != null ? resp.body().string() : "";
                    if (!resp.isSuccessful()) {
                        throw new Exception("API HTTP " + resp.code() + ": " + respBody);
                    }
                    JSONObject json = new JSONObject(respBody);
                    boolean status = json.optBoolean("status", false);
                    String code = json.optString("code", "");
                    if (!status || code == null || code.isEmpty()) {
                        throw new Exception("API status=false: " + respBody);
                    }
                    otp = code;
                }

                mainHandler.post(() -> {
                    account.setStatus("OTP: " + otp);
                    copyToClipboard(appCtx, otp);
                    Toast.makeText(appCtx, "OTP: " + otp, Toast.LENGTH_SHORT).show();
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
