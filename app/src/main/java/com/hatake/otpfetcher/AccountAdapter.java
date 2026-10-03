package com.hatake.otpfetcher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hatake.otpfetcher.db.Account;
import com.hatake.otpfetcher.db.AppDbHelper;

import org.json.JSONArray;
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
 * Account rows for SessionDetailsActivity.
 *
 * <p>Row: email (tap = copy), OTP/status TextView, platform badge,
 * "Mark as Used" checkbox (persisted), Fetch OTP button.
 *
 * <p>Fetch: POST account JSON to the extractor API on a background
 * ExecutorService thread, parse {status, code, messages}, detect the
 * platform (Facebook / Instagram / TikTok), save OTP + platform to the
 * database, update UI on the main Looper, auto-copy the OTP.
 */
public class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.AccountViewHolder> {

    private static final String API_URL = "https://tools.dongvanfb.net/api/get_messages_oauth2";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final List<Account> accounts = new ArrayList<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final OkHttpClient httpClient = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AppDbHelper dbHelper;

    public AccountAdapter(Context context) {
        this.dbHelper = new AppDbHelper(context);
    }

    public void setAccounts(List<Account> newAccounts) {
        accounts.clear();
        accounts.addAll(newAccounts);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AccountViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_account, parent, false);
        return new AccountViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AccountViewHolder holder, int position) {
        Account account = accounts.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvEmail.setText(account.getEmail());

        // Status / OTP text.
        String status;
        if ("Loading...".equals(account.getStatus())) {
            status = "Loading...";
        } else if (account.getStatus().startsWith("Error:")) {
            status = account.getStatus();
        } else if (account.getOtp().isEmpty()) {
            status = "Ready";
        } else {
            status = "OTP: " + account.getOtp();
        }
        holder.tvStatus.setText(status);

        // Platform badge with brand color.
        String platform = account.getPlatformName();
        holder.tvPlatform.setText(platform.isEmpty() ? "—" : platform);
        holder.tvPlatform.setBackgroundTintList(
                ColorStateList.valueOf(platformColor(platform)));

        boolean loading = "Loading...".equals(account.getStatus());
        holder.btnFetch.setEnabled(!loading);

        // Mark as Used: restore state (null listener during bind), persist on toggle.
        holder.cbUsed.setOnCheckedChangeListener(null);
        holder.cbUsed.setChecked(account.isUsed());
        holder.cbUsed.setOnCheckedChangeListener((buttonView, isChecked) -> {
            account.setUsed(isChecked);
            executor.execute(() -> dbHelper.updateUsed(account.getId(), isChecked));
        });

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

    private void fetchOtp(Context ctx, Account account, AccountViewHolder holder) {
        final Context appCtx = ctx.getApplicationContext();
        final int adapterPos = holder.getBindingAdapterPosition();

        executor.execute(() -> {
            try {
                // Request payload: {email, pass, refresh_token, client_id}.
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

                final String otp;
                final String platform;
                try (Response resp = httpClient.newCall(request).execute()) {
                    String respBody = resp.body() != null ? resp.body().string() : "";
                    if (!resp.isSuccessful()) {
                        throw new Exception("API HTTP " + resp.code() + ": " + respBody);
                    }
                    JSONObject json = new JSONObject(respBody);
                    boolean status = json.optBoolean("status", false);
                    String code = json.optString("code", "");
                    if (!status || code.isEmpty()) {
                        throw new Exception("API status=false: " + respBody);
                    }
                    otp = code;
                    platform = detectPlatform(json);
                }

                // Persist OTP + detected platform.
                account.setOtp(otp);
                account.setPlatformName(platform);
                account.setStatus("");
                dbHelper.updateOtpAndPlatform(account.getId(), otp, platform);

                mainHandler.post(() -> {
                    if (adapterPos != RecyclerView.NO_POSITION) {
                        notifyItemChanged(adapterPos);
                    } else {
                        notifyDataSetChanged();
                    }
                    copyToClipboard(appCtx, otp);
                    Toast.makeText(appCtx, "OTP: " + otp + " (" + platform + ")",
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                final String err = "Error: " + e.getMessage();
                account.setStatus(err);
                mainHandler.post(() -> {
                    if (adapterPos != RecyclerView.NO_POSITION) {
                        notifyItemChanged(adapterPos);
                    } else {
                        notifyDataSetChanged();
                    }
                    Toast.makeText(appCtx, err, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Detect platform from the API response. Checks an explicit "platform"
     * field first, then scans each entry of "messages" (sender/subject/body),
     * then falls back to scanning the whole response text.
     */
    private String detectPlatform(JSONObject json) {
        try {
            String explicit = json.optString("platform", "");
            if (!explicit.isEmpty()) {
                String p = platformFromText(explicit.toLowerCase());
                if (p != null) return p;
            }
            JSONArray messages = json.optJSONArray("messages");
            if (messages != null) {
                for (int i = 0; i < messages.length(); i++) {
                    Object item = messages.opt(i);
                    if (item == null) continue;
                    String p = platformFromText(item.toString().toLowerCase());
                    if (p != null) return p;
                }
            }
            String p = platformFromText(json.toString().toLowerCase());
            return p != null ? p : "Other";
        } catch (Exception e) {
            return "Other";
        }
    }

    private String platformFromText(String text) {
        if (text.contains("facebook")) return "Facebook";
        if (text.contains("instagram")) return "Instagram";
        if (text.contains("tiktok")) return "TikTok";
        return null;
    }

    private int platformColor(String platform) {
        switch (platform) {
            case "Facebook": return Color.parseColor("#1877F2");
            case "Instagram": return Color.parseColor("#E1306C");
            case "TikTok": return Color.parseColor("#000000");
            default: return Color.parseColor("#757575");
        }
    }

    private void copyToClipboard(Context ctx, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("otp", text));
        }
    }

    static class AccountViewHolder extends RecyclerView.ViewHolder {
        final TextView tvEmail;
        final TextView tvStatus;
        final TextView tvPlatform;
        final CheckBox cbUsed;
        final Button btnFetch;

        AccountViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEmail = itemView.findViewById(R.id.tvEmail);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvPlatform = itemView.findViewById(R.id.tvPlatform);
            cbUsed = itemView.findViewById(R.id.cbUsed);
            btnFetch = itemView.findViewById(R.id.btnFetch);
        }
    }
}
