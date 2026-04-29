package com.example.possantransbusticketingsystemapp;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter {

    private static final int VIEW_TYPE_SENT_TEXT = 1;
    private static final int VIEW_TYPE_RECEIVED_TEXT = 2;
    private static final int VIEW_TYPE_SENT_IMAGE = 3;
    private static final int VIEW_TYPE_RECEIVED_IMAGE = 4;
    private static final int VIEW_TYPE_SENT_VIDEO = 5;
    private static final int VIEW_TYPE_RECEIVED_VIDEO = 6;

    private final List<Message> messageList;
    private final String currentUserName;
    private final Context context;

    public MessageAdapter(List<Message> messageList, String currentUserName, Context context) {
        this.messageList = messageList;
        this.currentUserName = currentUserName;
        this.context = context;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messageList.get(position);
        boolean isSent = message.getSender() != null && message.getSender().equals(currentUserName);

        switch (message.getType()) {
            case "IMAGE":
                return isSent ? VIEW_TYPE_SENT_IMAGE : VIEW_TYPE_RECEIVED_IMAGE;
            case "VIDEO":
                return isSent ? VIEW_TYPE_SENT_VIDEO : VIEW_TYPE_RECEIVED_VIDEO;
            default: // TEXT
                return isSent ? VIEW_TYPE_SENT_TEXT : VIEW_TYPE_RECEIVED_TEXT;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_TYPE_SENT_TEXT:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent, parent, false);
                return new SentMessageHolder(view);
            case VIEW_TYPE_RECEIVED_TEXT:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
                return new ReceivedMessageHolder(view);
            case VIEW_TYPE_SENT_IMAGE:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent_image, parent, false);
                return new SentImageHolder(view);
            case VIEW_TYPE_RECEIVED_IMAGE:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received_image, parent, false);
                return new ReceivedImageHolder(view);
            case VIEW_TYPE_SENT_VIDEO:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent_video, parent, false);
                return new SentVideoHolder(view);
            case VIEW_TYPE_RECEIVED_VIDEO:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received_video, parent, false);
                return new ReceivedVideoHolder(view);
            default:
                // Fallback to a simple text view to avoid crash
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
                return new ReceivedMessageHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messageList.get(position);

        switch (holder.getItemViewType()) {
            case VIEW_TYPE_SENT_TEXT:
                ((SentMessageHolder) holder).bind(message);
                break;
            case VIEW_TYPE_RECEIVED_TEXT:
                ((ReceivedMessageHolder) holder).bind(message);
                break;
            case VIEW_TYPE_SENT_IMAGE:
                ((SentImageHolder) holder).bind(message);
                break;
            case VIEW_TYPE_RECEIVED_IMAGE:
                ((ReceivedImageHolder) holder).bind(message);
                break;
            case VIEW_TYPE_SENT_VIDEO:
                ((SentVideoHolder) holder).bind(message);
                break;
            case VIEW_TYPE_RECEIVED_VIDEO:
                ((ReceivedVideoHolder) holder).bind(message);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    // ViewHolder for sent text messages
    private class SentMessageHolder extends RecyclerView.ViewHolder {
        TextView messageTextView;
        SentMessageHolder(View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
        }
        void bind(Message message) {
            messageTextView.setText(message.getText());
        }
    }

    // ViewHolder for received text messages
    private class ReceivedMessageHolder extends RecyclerView.ViewHolder {
        TextView messageTextView, senderTextView;
        ReceivedMessageHolder(View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
            senderTextView = itemView.findViewById(R.id.senderTextView);
        }
        void bind(Message message) {
            messageTextView.setText(message.getText());
            senderTextView.setText(message.getSender());
        }
    }

    // ViewHolder for sent image messages
    private class SentImageHolder extends RecyclerView.ViewHolder {
        ImageView messageImageView;
        SentImageHolder(View itemView) {
            super(itemView);
            messageImageView = itemView.findViewById(R.id.messageImageView);
        }
        void bind(Message message) {
            Glide.with(context).load(message.getImageUrl()).into(messageImageView);
        }
    }

    // ViewHolder for received image messages
    private class ReceivedImageHolder extends RecyclerView.ViewHolder {
        ImageView messageImageView;
        TextView senderTextView;
        ReceivedImageHolder(View itemView) {
            super(itemView);
            messageImageView = itemView.findViewById(R.id.messageImageView);
            senderTextView = itemView.findViewById(R.id.senderTextView);
        }
        void bind(Message message) {
            senderTextView.setText(message.getSender());
            Glide.with(context).load(message.getImageUrl()).into(messageImageView);
        }
    }

    // ViewHolder for sent video messages
    private class SentVideoHolder extends RecyclerView.ViewHolder {
        SentVideoHolder(View itemView) {
            super(itemView);
        }
        void bind(Message message) {
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(message.getVideoUrl()));
                context.startActivity(intent);
            });
        }
    }

    // ViewHolder for received video messages
    private class ReceivedVideoHolder extends RecyclerView.ViewHolder {
        TextView senderTextView;
        ReceivedVideoHolder(View itemView) {
            super(itemView);
            senderTextView = itemView.findViewById(R.id.senderTextView);
        }
        void bind(Message message) {
            senderTextView.setText(message.getSender());
            itemView.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(message.getVideoUrl()));
                context.startActivity(intent);
            });
        }
    }
}
