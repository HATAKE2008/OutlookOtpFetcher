package com.hatake.otpfetcher;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hatake.otpfetcher.db.Account;
import com.hatake.otpfetcher.db.AppDbHelper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Session details: list of all accounts in one session.
 * Each row shows email, OTP/status, platform badge, "Mark as Used" checkbox,
 * and a Fetch OTP button.
 */
public class SessionDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_SESSION_TITLE = "session_title";

    private long sessionId = -1;
    private RecyclerView rvAccounts;
    private AccountAdapter accountAdapter;
    private AppDbHelper dbHelper;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_details);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, -1);
        String title = getIntent().getStringExtra(EXTRA_SESSION_TITLE);
        if (title != null && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }

        dbHelper = new AppDbHelper(this);

        rvAccounts = findViewById(R.id.rvAccounts);
        accountAdapter = new AccountAdapter(this);
        rvAccounts.setLayoutManager(new LinearLayoutManager(this));
        rvAccounts.setAdapter(accountAdapter);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAccounts();
    }

    private void loadAccounts() {
        if (sessionId < 0) return;
        executor.execute(() -> {
            List<Account> list = dbHelper.getAccountsForSession(sessionId);
            mainHandler.post(() -> accountAdapter.setAccounts(list));
        });
    }
}
