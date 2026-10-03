package com.hatake.otpfetcher;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hatake.otpfetcher.db.AppDbHelper;
import com.hatake.otpfetcher.db.Session;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Home screen: bulk-paste accounts, save as a new Session, list all Sessions.
 * Toolbar has a Settings icon -> SettingsActivity.
 */
public class MainActivity extends AppCompatActivity {

    private EditText etPaste;
    private Button btnSave;
    private RecyclerView rvSessions;
    private SessionAdapter sessionAdapter;
    private AppDbHelper dbHelper;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat titleFmt = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        dbHelper = new AppDbHelper(this);

        etPaste = findViewById(R.id.etPaste);
        btnSave = findViewById(R.id.btnSave);
        rvSessions = findViewById(R.id.rvSessions);

        sessionAdapter = new SessionAdapter(this::openSessionDetails);
        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        rvSessions.setAdapter(sessionAdapter);

        btnSave.setOnClickListener(v -> saveSession());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSessions();
    }

    private void saveSession() {
        String raw = etPaste.getText() != null ? etPaste.getText().toString() : "";
        List<String[]> rows = parseRows(raw);
        if (rows.isEmpty()) {
            Toast.makeText(this,
                    "No valid lines (email|password|refresh_token|client_id)",
                    Toast.LENGTH_LONG).show();
            return;
        }
        btnSave.setEnabled(false);
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            String title = "Hotmails - " + titleFmt.format(new Date(now));
            long sessionId = dbHelper.insertSession(title, now);
            for (String[] p : rows) {
                dbHelper.insertAccount(sessionId, p[0], p[1], p[2], p[3]);
            }
            final int count = rows.size();
            final String sessionTitle = title;
            mainHandler.post(() -> {
                etPaste.setText("");
                btnSave.setEnabled(true);
                Toast.makeText(this,
                        "Saved " + count + " accounts to '" + sessionTitle + "'",
                        Toast.LENGTH_LONG).show();
                loadSessions();
            });
        });
    }

    /** Parse "email|password|refresh_token|client_id" lines. */
    private List<String[]> parseRows(String raw) {
        List<String[]> rows = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("\\|", -1);
            if (parts.length != 4) continue;
            String email = parts[0].trim();
            String password = parts[1].trim();
            String refreshToken = parts[2].trim();
            String clientId = parts[3].trim();
            if (email.isEmpty() || refreshToken.isEmpty() || clientId.isEmpty()) continue;
            rows.add(new String[]{email, password, refreshToken, clientId});
        }
        return rows;
    }

    private void loadSessions() {
        executor.execute(() -> {
            List<Session> sessions = dbHelper.getAllSessions();
            for (Session s : sessions) {
                s.setAccountCount(dbHelper.getAccountCount(s.getId()));
            }
            mainHandler.post(() -> sessionAdapter.setSessions(sessions));
        });
    }

    private void openSessionDetails(Session session) {
        Intent intent = new Intent(this, SessionDetailsActivity.class);
        intent.putExtra(SessionDetailsActivity.EXTRA_SESSION_ID, session.getId());
        intent.putExtra(SessionDetailsActivity.EXTRA_SESSION_TITLE, session.getTitle());
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
