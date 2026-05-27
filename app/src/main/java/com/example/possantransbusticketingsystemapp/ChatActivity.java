package com.example.possantransbusticketingsystemapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private static final int RC_PHOTO_PICKER = 2;
    private static final int RC_VIDEO_PICKER = 3;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;

    private RecyclerView chatRecyclerView;
    private EditText messageEditText;
    private ImageButton sendButton;
    private ImageButton attachButton;
    private MessageAdapter messageAdapter;
    private List<Message> messageList;

    private DatabaseReference messagesDatabaseReference;
    private FirebaseStorage firebaseStorage;
    private StorageReference chatPhotosStorageReference;
    private StorageReference chatVideosStorageReference;

    private String conductorName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        SharedPreferences mainPrefs = getSharedPreferences("SantransPrefs", MODE_PRIVATE);
        conductorName = mainPrefs.getString("conductorName", "Conductor");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Chat - General");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        messagesDatabaseReference = FirebaseDatabase.getInstance().getReference().child("messages");
        firebaseStorage = FirebaseStorage.getInstance();
        chatPhotosStorageReference = firebaseStorage.getReference().child("chat_photos");
        chatVideosStorageReference = firebaseStorage.getReference().child("chat_videos");

        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        messageEditText = findViewById(R.id.messageEditText);
        sendButton = findViewById(R.id.sendButton);
        attachButton = findViewById(R.id.attachButton);

        messageList = new ArrayList<>();
        messageAdapter = new MessageAdapter(messageList, conductorName, this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);
        chatRecyclerView.setAdapter(messageAdapter);

        sendButton.setOnClickListener(v -> sendMessage());

        attachButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
            } else {
                showAttachmentOptions();
            }
        });

        attachDatabaseReadListener();
    }

    private void showAttachmentOptions() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Attachment Type");
        builder.setItems(new CharSequence[]{"Image", "Video"}, (dialog, which) -> {
            switch (which) {
                case 0:
                    openImagePicker();
                    break;
                case 1:
                    openVideoPicker();
                    break;
            }
        });
        builder.show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/jpeg");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
    }

    private void openVideoPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_VIDEO_PICKER);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showAttachmentOptions();
            } else {
                Toast.makeText(this, "Permission denied to read your External storage", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            if (requestCode == RC_PHOTO_PICKER) {
                uploadFile(data.getData(), "IMAGE", chatPhotosStorageReference);
            } else if (requestCode == RC_VIDEO_PICKER) {
                uploadFile(data.getData(), "VIDEO", chatVideosStorageReference);
            }
        }
    }

    private void uploadFile(Uri fileUri, final String type, StorageReference storageReference) {
        Toast.makeText(this, "Uploading " + type.toLowerCase() + "...", Toast.LENGTH_SHORT).show();
        final StorageReference fileRef = storageReference.child(fileUri.getLastPathSegment() + "_" + System.currentTimeMillis());

        fileRef.putFile(fileUri).addOnSuccessListener(this, taskSnapshot -> {
            fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                Message message = new Message(uri.toString(), conductorName, type);
                messagesDatabaseReference.push().setValue(message);
            });
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void sendMessage() {
        String messageText = messageEditText.getText().toString().trim();
        if (TextUtils.isEmpty(messageText)) {
            return;
        }
        Message message = new Message(messageText, conductorName);
        messagesDatabaseReference.push().setValue(message);
        messageEditText.setText("");
    }

    private void attachDatabaseReadListener() {
        messagesDatabaseReference.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Message message = snapshot.getValue(Message.class);
                if (message != null) {
                    messageList.add(message);
                    messageAdapter.notifyItemInserted(messageList.size() - 1);
                    chatRecyclerView.scrollToPosition(messageList.size() - 1);
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ChatActivity.this, "Failed to load messages.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
