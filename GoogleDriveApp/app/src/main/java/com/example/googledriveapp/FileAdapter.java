package com.example.googledriveapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.api.services.drive.model.File;

import java.util.List;
public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {
    private List<File> files;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(File file);
        void onDownloadClick(File file);
        void onEditClick(File file); // Add this method
        void onDeleteClick(File file);
    }

    public FileAdapter(List<File> files, OnItemClickListener listener) {
        this.files = files;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }
    public void updateFiles(List<File> newFiles) {
        files.clear();
        files.addAll(newFiles);
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        File file = files.get(position);
        holder.fileNameTextView.setText(file.getName());

        holder.itemView.setOnClickListener(v -> listener.onItemClick(file));
        holder.downloadButton.setOnClickListener(v -> listener.onDownloadClick(file));
        holder.editButton.setOnClickListener(v -> listener.onEditClick(file)); // Notify on edit
        holder.deleteButton.setOnClickListener(v -> listener.onDeleteClick(file));
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileNameTextView;
        ImageButton downloadButton;
        ImageButton editButton;
        ImageButton deleteButton;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            fileNameTextView = itemView.findViewById(R.id.file_name_text_view);
            downloadButton = itemView.findViewById(R.id.download_button);
            editButton = itemView.findViewById(R.id.edit_button);
            deleteButton = itemView.findViewById(R.id.delete_button);
        }

    }
}
