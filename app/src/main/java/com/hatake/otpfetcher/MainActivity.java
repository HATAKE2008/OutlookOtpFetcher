package com.hatake.otpfetcher;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Paste multiple accounts (one per line: email|password|refresh_token|client_id),
 * tap "Load Accounts" to populate the RecyclerView, then "Fetch OTP" per row.
 */
public class MainActivity extends AppCompatActivity {

    private EditText etAccounts;
    private Button btnLoad;
    private RecyclerView rvAccounts;
    private OtpAccountAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etAccounts = findViewById(R.id.etAccounts);
        btnLoad = findViewById(R.id.btnLoad);
        rvAccounts = findViewById(R.id.rvAccounts);

        adapter = new OtpAccountAdapter();
        rvAccounts.setLayoutManager(new LinearLayoutManager(this));
        rvAccounts.setAdapter(adapter);

        btnLoad.setOnClickListener(v -> loadAccounts());
    }

    private void loadAccounts() {
        String raw = etAccounts.getText() != null ? etAccounts.getText().toString() : "";
        List<OtpAccount> parsed = new ArrayList<>();

        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // email|password|refresh_token|client_id
            String[] parts = trimmed.split("\\|", -1);
            if (parts.length != 4) {
                continue;
            }
            String email = parts[0].trim();
            String password = parts[1].trim();
            String refreshToken = parts[2].trim();
            String clientId = parts[3].trim();
            if (email.isEmpty() || refreshToken.isEmpty() || clientId.isEmpty()) {
                continue;
            }
            parsed.add(new OtpAccount(email, password, refreshToken, clientId));
        }

        adapter.setAccounts(parsed);
        Toast.makeText(this, "Loaded " + parsed.size() + " accounts", Toast.LENGTH_SHORT).show();
    }
}
