package com.hatake.otpfetcher;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hatake.otpfetcher.db.Session;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Sessions list for the home screen. */
public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(Session session);
    }

    private final List<Session> sessions = new ArrayList<>();
    private final OnSessionClickListener listener;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.US);

    public SessionAdapter(OnSessionClickListener listener) {
        this.listener = listener;
    }

    public void setSessions(List<Session> newSessions) {
        sessions.clear();
        sessions.addAll(newSessions);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        Session session = sessions.get(position);
        holder.tvTitle.setText(session.getTitle());
        String meta = session.getAccountCount() + " accounts • "
                + timeFmt.format(new Date(session.getCreatedAt()));
        holder.tvMeta.setText(meta);
        holder.itemView.setOnClickListener(v -> listener.onSessionClick(session));
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvMeta;

        SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvSessionTitle);
            tvMeta = itemView.findViewById(R.id.tvSessionMeta);
        }
    }
}
