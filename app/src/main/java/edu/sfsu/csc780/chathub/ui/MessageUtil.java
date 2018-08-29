package edu.sfsu.csc780.chathub.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.Calendar;

import de.hdodenhof.circleimageview.CircleImageView;
import edu.sfsu.csc780.chathub.model.ChatMessage;
import edu.sfsu.csc780.chathub.R;

public class MessageUtil {
    private static final String LOG_TAG = MessageUtil.class.getSimpleName();
    public static final String MESSAGES_CHILD = "messages";
    private static DatabaseReference sFirebaseDatabaseReference =
            FirebaseDatabase.getInstance().getReference();
    private static FirebaseStorage sStorage = FirebaseStorage.getInstance();
    private static MessageLoadListener sAdapterListener;
    private static FirebaseAuth sFirebaseAuth;
    public static View.OnClickListener sMessageClickListener;

    private static Bitmap thumb;

    public interface MessageLoadListener { public void onLoadComplete(); }

    public static void send(ChatMessage chatMessage) {
        sFirebaseDatabaseReference.child(MESSAGES_CHILD).push().setValue(chatMessage);
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        public TextView messageTextView;
        public ImageView messageImageView;
        public TextView messengerTextView;
        public VideoView messageVideoView;
        public CircleImageView messengerImageView;
        public TextView timestampTextView;
        public View messageLayout;
        public VideoView messageAudioView;
        public ImageView audioPlay;

        public MessageViewHolder(View v) {
            super(v);
            messageTextView = (TextView) itemView.findViewById(R.id.messageTextView);
            messengerTextView = (TextView) itemView.findViewById(R.id.messengerTextView);
            messengerImageView = (CircleImageView) itemView.findViewById(R.id.messengerImageView);
            messageVideoView = (VideoView) itemView.findViewById(R.id.messageVideoView);
            messageAudioView = (VideoView) itemView.findViewById(R.id.messageAudioView);
            messageImageView = (ImageView) itemView.findViewById(R.id.messageImageView);
            timestampTextView = (TextView) itemView.findViewById(R.id.timestampTextView);
            messageLayout = (View) itemView.findViewById(R.id.messageLayout);
            audioPlay = (ImageView) itemView.findViewById(R.id.audioPlayButton);

            v.setOnClickListener(sMessageClickListener);

        }
    }

    public static FirebaseRecyclerAdapter getFirebaseAdapter(final Activity activity,
                                                             MessageLoadListener listener,
                                                             final LinearLayoutManager linearManager,
                                                             final RecyclerView recyclerView,
                                                             View.OnClickListener onClickListener) {
        sMessageClickListener = onClickListener;
        final int fontSize = DesignUtils.getFontSize(activity);
        final SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(activity);
        sAdapterListener = listener;
        final FirebaseRecyclerAdapter adapter = new FirebaseRecyclerAdapter<ChatMessage,
                MessageViewHolder>(
                ChatMessage.class,
                R.layout.item_message,
                MessageViewHolder.class,
                sFirebaseDatabaseReference.child(MESSAGES_CHILD)) {
            @Override
            protected void populateViewHolder(final MessageViewHolder viewHolder,
                                              ChatMessage chatMessage, int position) {
                sAdapterListener.onLoadComplete();
                viewHolder.messageTextView.setTextSize(fontSize);
                viewHolder.messageTextView.setText(chatMessage.getText());
                viewHolder.messengerTextView.setTextSize(fontSize - 5);
                viewHolder.messengerTextView.setText(chatMessage.getName());
                viewHolder.timestampTextView.setTextSize(fontSize - 5);
                if (chatMessage.getPhotoUrl() == null) {
                    viewHolder.messengerImageView
                            .setImageDrawable(ContextCompat
                                    .getDrawable(activity,
                                            R.drawable.ic_account_circle_black_36dp));
                } else {
                    SimpleTarget target = new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(Bitmap bitmap, GlideAnimation glideAnimation) {
                            viewHolder.messengerImageView.setImageBitmap(bitmap);
                            final String palettePreference = activity.getString(R.string
                                    .auto_palette_preference);

                            if (preferences.getBoolean(palettePreference, false)) {
                                DesignUtils.setBackgroundFromPalette(bitmap, viewHolder
                                        .messageLayout);
                            } else {
                                viewHolder.messageLayout.setBackground(
                                        activity.getResources().getDrawable(
                                                R.drawable.message_background));
                            }

                        }
                    };
                    Glide.with(activity)
                            .load(chatMessage.getPhotoUrl())
                            .asBitmap()
                            .into(target);
                }

                if (chatMessage.getMediaUrl() != null && chatMessage.getMediaType() == 1) { //Image message

                    viewHolder.messageVideoView.setVisibility(View.GONE);
                    viewHolder.messageImageView.setVisibility(View.VISIBLE);
                    viewHolder.messageTextView.setVisibility(View.GONE);
                    viewHolder.messageAudioView.setVisibility(View.GONE);
                    viewHolder.audioPlay.setVisibility(View.GONE);

                    try {
                        final StorageReference gsReference =
                                sStorage.getReferenceFromUrl(chatMessage.getMediaUrl());
                        gsReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri uri) {
                                Glide.with(activity)
                                        .load(uri)
                                        .into(viewHolder.messageImageView);
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception exception) {
                                Log.e(LOG_TAG, "Could not load image for message", exception);
                            }
                        });
                    } catch (IllegalArgumentException e) {
                        viewHolder.messageTextView.setText("Error loading image");
                        Log.e(LOG_TAG, e.getMessage() + " : " + chatMessage.getMediaUrl());
                    }
                }
                else if (chatMessage.getMediaUrl() != null && chatMessage.getMediaType() == 2){ // Video Message

                    viewHolder.messageImageView.setVisibility(View.GONE);
                    viewHolder.messageVideoView.setVisibility(View.VISIBLE);
                    viewHolder.messageTextView.setVisibility(View.GONE);
                    viewHolder.messageAudioView.setVisibility(View.GONE);
                    viewHolder.audioPlay.setVisibility(View.GONE);
                    viewHolder.messageVideoView.setBackgroundResource(R.drawable.video_play);


                    try {
                        final StorageReference gsReference =
                                sStorage.getReferenceFromUrl(chatMessage.getMediaUrl());
                        gsReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri uri) {
                                viewHolder.messageVideoView.setVideoURI(uri);
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception exception) {
                                Log.e(LOG_TAG, "Could not load video", exception);
                            }
                        });
                    } catch (IllegalArgumentException e) {
                        viewHolder.messageTextView.setText("Error loading video");
                        Log.e(LOG_TAG, e.getMessage() + " : " + chatMessage.getMediaUrl());
                    }
                }
                else if (chatMessage.getMediaUrl() != null && chatMessage.getMediaType() == 3){   // Audio Message

                    viewHolder.messageVideoView.setVisibility(View.GONE);
                    viewHolder.messageTextView.setVisibility(View.GONE);
                    viewHolder.messageImageView.setVisibility(View.GONE);
                    viewHolder.messageAudioView.setVisibility(View.VISIBLE);
                    viewHolder.audioPlay.setVisibility(View.GONE);

                    viewHolder.messageAudioView.setBackgroundResource(R.drawable.voice_message);
                   // viewHolder.messageVideoView.setAlpha(0.2f);

                    try {
                        final StorageReference gsReference =
                                sStorage.getReferenceFromUrl(chatMessage.getMediaUrl());
                        gsReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                            @Override
                            public void onSuccess(Uri uri) {
                                viewHolder.messageAudioView.setVideoURI(uri);
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception exception) {
                                Log.e(LOG_TAG, "Could not load audio", exception);
                            }
                        });
                    } catch (IllegalArgumentException e) {
                        viewHolder.messageTextView.setText("Error loading audio");
                        Log.e(LOG_TAG, e.getMessage() + " : " + chatMessage.getMediaUrl());
                    }
                }
                else {
                    /*if (chatMessage.getIsImage() == 1) {
                        viewHolder.messageImageView.setVisibility(View.GONE);
                    } else if (chatMessage.getIsImage() == 2){
                        viewHolder.messageVideoView.setVisibility(View.GONE);
                    }*/
                    viewHolder.messageImageView.setVisibility(View.GONE);
                    viewHolder.messageVideoView.setVisibility(View.GONE);
                    viewHolder.messageAudioView.setVisibility(View.GONE);
                    viewHolder.audioPlay.setVisibility(View.GONE);
                    viewHolder.messageTextView.setVisibility(View.VISIBLE);
                }

                long timestamp = chatMessage.getTimestamp();
                if (timestamp == 0 || timestamp == chatMessage.NO_TIMESTAMP ) {
                    viewHolder.timestampTextView.setVisibility(View.GONE);
                } else {
                    viewHolder.timestampTextView.setText(DesignUtils.formatTime(activity,
                            timestamp));
                    viewHolder.timestampTextView.setVisibility(View.VISIBLE);
                }
            }
        };

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                int messageCount = adapter.getItemCount();
                int lastVisiblePosition = linearManager.findLastCompletelyVisibleItemPosition();
                if (lastVisiblePosition == -1 ||
                        (positionStart >= (messageCount - 1) &&
                                lastVisiblePosition == (positionStart - 1))) {
                    recyclerView.scrollToPosition(positionStart);
                }
            }
        });
        return adapter;
    }

    public static StorageReference getImageStorageReference(FirebaseUser user, Uri uri) {
        //Create a blob storage reference with path : bucket/userId/timeMs/filename
        long nowMs = Calendar.getInstance().getTimeInMillis();

        return sStorage.getReference().child(user.getUid() + "/" + nowMs + "/" + uri
                .getLastPathSegment());
    }

}
