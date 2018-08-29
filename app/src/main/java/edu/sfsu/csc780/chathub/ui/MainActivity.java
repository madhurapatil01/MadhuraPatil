/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.sfsu.csc780.chathub.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import com.bumptech.glide.load.resource.bitmap.GlideBitmapDrawable;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import edu.sfsu.csc780.chathub.R;
import edu.sfsu.csc780.chathub.model.ChatMessage;

public class MainActivity extends AppCompatActivity
        implements GoogleApiClient.OnConnectionFailedListener,
        MessageUtil.MessageLoadListener {

    private static final String TAG = "MainActivity";
    public static final String MESSAGES_CHILD = "messages";
    private static final int REQUEST_INVITE = 1;
    public static final int REQUEST_PREFERENCES = 2;
    public static final int MSG_LENGTH_LIMIT = 64;
    private static final double MAX_LINEAR_DIMENSION = 500.0;
    public static final String ANONYMOUS = "anonymous";
    private static final int REQUEST_PICK_IMAGE = 1;
    private static final int REQUEST_IMAGE_CAPTURE = 23;
    private static final int REQUEST_VIDEO_CAPTURE = 25;
    private static int REQUEST_AUDIO_CAPTURE = 0;
    private String mUsername;
    private String mPhotoUrl;
    private SharedPreferences mSharedPreferences;
    private GoogleApiClient mGoogleApiClient;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ShakeDetector mShakeDetector;
    private String shakePreference;
    private String soundPreference;

    private FloatingActionButton mSendButton;
    private RecyclerView mMessageRecyclerView;
    private LinearLayoutManager mLinearLayoutManager;
    private ProgressBar mProgressBar;
    private EditText mMessageEditText;

    // Firebase instance variables
    private FirebaseAuth mAuth;
    private FirebaseUser mUser;

    private FirebaseRecyclerAdapter<ChatMessage, MessageUtil.MessageViewHolder>
            mFirebaseAdapter;
    private ImageButton mImageButton;
    private int mSavedTheme;
    private int mSavedBackground;
    private int mSavedFontSize;
    private ImageButton mLocationButton;
    private ImageButton mCameraButton;
    private ImageButton mVoiceMessageButton;
    private ImageButton mVideoButton;

    public static int featureFlag = 0;
    private static final String AUDIO_RECORDER_FILE_EXT_3GP = ".3gp";
    private static final String AUDIO_RECORDER_FILE_EXT_MP4 = ".mp4";
    private static final String AUDIO_RECORDER_FOLDER = "AudioRecorder";
    private MediaRecorder recorder = null;
    private int currentFormat = 0;
    private int output_formats[] = { MediaRecorder.OutputFormat.MPEG_4,             MediaRecorder.OutputFormat.THREE_GPP };
    private String file_exts[] = { AUDIO_RECORDER_FILE_EXT_MP4, AUDIO_RECORDER_FILE_EXT_3GP };
    private String audioUri = null;

    private MediaPlayer mSentSound;
    private MediaPlayer mStartRecordSound;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DesignUtils.applyColorfulTheme(this);
        setContentView(R.layout.activity_main);
        final Context context = this;

        soundPreference = this.getString(R.string.play_sounds);
        mSentSound = MediaPlayer.create(context,R.raw.sentmessage);
        mStartRecordSound = MediaPlayer.create(context,R.raw.recording_beep);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        // Set default username is anonymous.
        mUsername = ANONYMOUS;
        //Initialize Auth
        mAuth = FirebaseAuth.getInstance();
        mUser = mAuth.getCurrentUser();
        if (mUser == null) {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        } else {
            mUsername = mUser.getDisplayName();
            if (mUser.getPhotoUrl() != null) {
                mPhotoUrl = mUser.getPhotoUrl().toString();
            }
        }

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API)
                .build();

        // Initialize ProgressBar and RecyclerView.
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageRecyclerView = (RecyclerView) findViewById(R.id.messageRecyclerView);
        mLinearLayoutManager = new LinearLayoutManager(this);
        mLinearLayoutManager.setStackFromEnd(true);
        mMessageRecyclerView.setLayoutManager(mLinearLayoutManager);
        mFirebaseAdapter = MessageUtil.getFirebaseAdapter(this,
                this,  /* MessageLoadListener */
                mLinearLayoutManager,
                mMessageRecyclerView,
                mImageClickListener);
        mMessageRecyclerView.setAdapter(mFirebaseAdapter);
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        DesignUtils.setBackground(this);

        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MSG_LENGTH_LIMIT)});
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        mSendButton = (FloatingActionButton) findViewById(R.id.sendButton);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Send messages on click.
                //mMessageRecyclerView.scrollToPosition(0);
                ChatMessage chatMessage = new
                        ChatMessage(mMessageEditText.getText().toString(),
                        mUsername,
                        mPhotoUrl);
                MessageUtil.send(chatMessage);
                if(mSharedPreferences.getBoolean(soundPreference,true)) {
                    mSentSound.start();
                }
                mMessageEditText.setText("");
            }
        });

        mImageButton = (ImageButton) findViewById(R.id.shareImageButton);
        mImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage();
            }
        });

        mLocationButton = (ImageButton) findViewById(R.id.locationButton);
        mLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //mLocationButton.setEnabled(false);
                featureFlag=1;
                loadMap();
            }
        });

        mCameraButton = (ImageButton) findViewById(R.id.cameraButton);
        mCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchTakePhotoIntent();
            }
        });

        mVideoButton = (ImageButton) findViewById(R.id.videoButton);
        mVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dispatchRecordVideoIntent();
            }
        });

        mVoiceMessageButton = (ImageButton) findViewById(R.id.shareVoiceMessage);
        mVoiceMessageButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {

                switch(event.getAction()){
                    case MotionEvent.ACTION_DOWN:
                        if(mSharedPreferences.getBoolean(soundPreference,true)) {
                            mStartRecordSound.start();
                        }
                        while(mStartRecordSound.isPlaying());
                        startRecording();
                        break;
                    case MotionEvent.ACTION_UP:
                        stopRecording();
                        if(mSharedPreferences.getBoolean(soundPreference,true)) {
                            mSentSound.start();
                        }
                        break;
                }
                return false;
            }
        });

        //Initialize the Accelerometer for Shake function
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // Use the accelerometer.
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mShakeDetector = new ShakeDetector();
        shakePreference = this.getString(R.string.shake_change_background);

        if(mSharedPreferences.getBoolean(shakePreference,true)){
            //Shake to change bg
            mSensorManager.registerListener(mShakeDetector,mAccelerometer,SensorManager.SENSOR_DELAY_UI);
            mShakeDetector.setOnShakeListener(new ShakeDetector.OnShakeListener() {
                @Override
                public void onShake(int count) {
                    // if(count > 2)
                    DesignUtils.setRandomBackground(context);
                }
            });
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in.
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mSharedPreferences.getBoolean(shakePreference,true))
            mSensorManager.unregisterListener(mShakeDetector);
    }

    @Override
    public void onResume() {
        super.onResume();
        LocationUtils.startLocationUpdates(this);
        if(mSharedPreferences.getBoolean(shakePreference,true)){
            mSensorManager.registerListener(mShakeDetector, mAccelerometer,	SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        boolean isGranted = (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        if (isGranted && requestCode == LocationUtils.REQUEST_CODE) {
            LocationUtils.startLocationUpdates(this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu:
                mAuth.signOut();
                Auth.GoogleSignInApi.signOut(mGoogleApiClient);
                mUsername = ANONYMOUS;
                startActivity(new Intent(this, SignInActivity.class));
                return true;
            case R.id.preferences_menu:
                mSavedTheme = DesignUtils.getPreferredTheme(this);
                mSavedBackground = DesignUtils.getPreferredBackground(this);
                mSavedFontSize = DesignUtils.getPreferredFontSize(this);
                Intent i = new Intent(this, PreferencesActivity.class);
                startActivityForResult(i, REQUEST_PREFERENCES);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onLoadComplete() {
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);
    }

    private void pickImage() {
        // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file browser
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        // Filter to only show results that can be "opened"
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Filter to show only images, using the image MIME data type.
        intent.setType("image/*");

        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: request=" + requestCode + ", result=" + resultCode);

        if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            // Process selected image here
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            if (data != null) {
                Uri uri = data.getData();
                Log.i(TAG, "Uri: " + uri.toString());

                // Resize if too big for messaging
                Bitmap bitmap = getBitmapForUri(uri);
                Bitmap resizedBitmap = scaleImage(bitmap);
                if (bitmap != resizedBitmap) {
                    uri = savePhotoImage(resizedBitmap);
                }

                createImageMessage(uri);
            } else {
                Log.e(TAG, "Cannot get image for uploading");
            }
        } else if (requestCode == REQUEST_PREFERENCES) {
            this.recreate();
        } else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK){
            if(data != null) {
                Bundle extras = data.getExtras();
                if (extras != null) {
                    Bitmap imageBitmap = (Bitmap) extras.get("data");
                    Uri uri = savePhotoImage(imageBitmap);
                    createImageMessage(uri);
                }
            }
        }else if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == Activity.RESULT_OK){
            if(data != null) {
                Uri uri = data.getData();
                createVideoMessage(uri);
            }
        }
    }

    private void createImageMessage(Uri uri) {
        if (uri == null) {
            Log.e(TAG, "Could not create image message with null uri");
            return;
        }

        final StorageReference imageReference = MessageUtil.getImageStorageReference(mUser, uri);
        UploadTask uploadTask = imageReference.putFile(uri);

        // Register observers to listen for when task is done or if it fails
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Log.e(TAG, "Failed to upload image message");
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                featureFlag =1;
                ChatMessage chatMessage = new
                        ChatMessage(mMessageEditText.getText().toString(),
                        mUsername,
                        mPhotoUrl, imageReference.toString(), featureFlag);
                MessageUtil.send(chatMessage);
                if(mSharedPreferences.getBoolean(soundPreference,true))
                    mSentSound.start();
                mMessageEditText.setText("");
            }
        });
    }

    private Bitmap getBitmapForUri(Uri imageUri) {
        Bitmap bitmap = null;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private Bitmap scaleImage(Bitmap bitmap) {
        int originalHeight = bitmap.getHeight();
        int originalWidth = bitmap.getWidth();
        double scaleFactor =  MAX_LINEAR_DIMENSION / (double)(originalHeight + originalWidth);

        // We only want to scale down images, not scale upwards
        if (scaleFactor < 1.0) {
            int targetWidth = (int) Math.round(originalWidth * scaleFactor);
            int targetHeight = (int) Math.round(originalHeight * scaleFactor);
            return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        } else {
            return bitmap;
        }
    }

    private Uri savePhotoImage(Bitmap imageBitmap) {
        File photoFile = null;
        try {
            photoFile = createImageFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (photoFile == null) {
            Log.d(TAG, "Error creating media file");
            return null;
        }

        try {
            FileOutputStream fos = new FileOutputStream(photoFile);
            imageBitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
        return Uri.fromFile(photoFile);
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        String imageFileNamePrefix = "chathub-" + timeStamp;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        File imageFile = File.createTempFile(
                imageFileNamePrefix,    /* prefix */
                ".jpg",                 /* suffix */
                storageDir              /* directory */
        );
        return imageFile;
    }

    private void createAudioMessage(Uri uri){
        if (uri == null){
            Log.e(TAG, "Could not create audio message with null uri");
            return;
        }

        final StorageReference audioReference = MessageUtil.getImageStorageReference(mUser, uri);
        UploadTask uploadTask = audioReference.putFile(uri);

        // Register observers to listen for when task is done or if it fails
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Log.e(TAG, "Failed to upload Audio message");
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                featureFlag = 3;
                ChatMessage chatMessage = new
                        ChatMessage(mMessageEditText.getText().toString(),
                        mUsername,
                        mPhotoUrl, audioReference.toString(),featureFlag);
                MessageUtil.send(chatMessage);
                mMessageEditText.setText("");
            }
        });
    }

    private void createVideoMessage(Uri uri) {
        if (uri == null) {
            Log.e(TAG, "Could not create video message with null uri");
            return;
        }

        final StorageReference videoReference = MessageUtil.getImageStorageReference(mUser, uri);
        UploadTask uploadTask = videoReference.putFile(uri);

        // Register observers to listen for when task is done or if it fails
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception exception) {
                Log.e(TAG, "Failed to upload video message");
            }
        }).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                featureFlag = 2;
                ChatMessage chatMessage = new
                        ChatMessage(mMessageEditText.getText().toString(),
                        mUsername,
                        mPhotoUrl, videoReference.toString(),featureFlag);
                MessageUtil.send(chatMessage);
                if(mSharedPreferences.getBoolean(soundPreference,true))
                    mSentSound.start();
                mMessageEditText.setText("");
            }
        });
    }

    private void loadMap() {
        Loader<Bitmap> loader = getSupportLoaderManager().initLoader(0, null, new LoaderManager
                .LoaderCallbacks<Bitmap>() {
            @Override
            public Loader<Bitmap> onCreateLoader(final int id, final Bundle args) {
                return new MapLoader(MainActivity.this);
            }

            @Override
            public void onLoadFinished(final Loader<Bitmap> loader, final Bitmap result) {
                mProgressBar.setVisibility(ProgressBar.INVISIBLE);
                mLocationButton.setEnabled(true);

                if (result == null) return;
                // Resize if too big for messaging
                Bitmap resizedBitmap = scaleImage(result);
                Uri uri = null;
                if (result != resizedBitmap) {
                    uri = savePhotoImage(resizedBitmap);
                } else {
                    uri = savePhotoImage(result);
                }
                createImageMessage(uri);
            }

            @Override
            public void onLoaderReset(final Loader<Bitmap> loader) {
            }

        });

        mProgressBar.setVisibility(ProgressBar.VISIBLE);
        mLocationButton.setEnabled(false);
        loader.forceLoad();
    }

    private void dispatchTakePhotoIntent(){
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(cameraIntent,0);
        boolean isIntentSafe = activities.size() > 0;
        featureFlag = 1;

        if(isIntentSafe){
            startActivityForResult(cameraIntent,REQUEST_IMAGE_CAPTURE);
        }
    }

    private void dispatchRecordVideoIntent(){
        Intent cameraIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        PackageManager packageManager = getPackageManager();
        List<ResolveInfo> activities = packageManager.queryIntentActivities(cameraIntent,0);
        boolean isIntentSafe = activities.size() > 0;
        featureFlag = 2;

        if(isIntentSafe){
            startActivityForResult(cameraIntent,REQUEST_VIDEO_CAPTURE);
        }
    }

    private View.OnClickListener mImageClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ImageView photoView = (ImageView) v.findViewById(R.id.messageImageView);
            VideoView videoView = (VideoView) v.findViewById(R.id.messageVideoView);
            VideoView audioView = (VideoView) v.findViewById(R.id.messageAudioView);
// Only show the larger view in dialog if there's a image for the message
            if (photoView.getVisibility() == View.VISIBLE) {
                Bitmap bitmap = ((GlideBitmapDrawable) photoView.getDrawable()).getBitmap();
                showPhotoDialog(ImageDialogFragment.newInstance(bitmap));
            }else if (videoView.getVisibility() == View.VISIBLE){
                if(videoView.isPlaying()){
                    videoView.pause();
                    videoView.setBackgroundResource(R.drawable.pause);
                }
                else {
                    videoView.setBackgroundResource(0);
                    videoView.start();
                }
            }else if(audioView.getVisibility() == View.VISIBLE){
                if(audioView.isPlaying()){
                    audioView.pause();
                    audioView.setBackgroundResource(R.drawable.pause);
                }
                else{
                audioView.start();
                    audioView.setBackgroundResource(R.drawable.voice_message);
                }
            }
        }
    };

    void showPhotoDialog(DialogFragment dialogFragment) {
// DialogFragment.show() will take care of adding the fragment
// in a transaction. We also want to remove any currently showing
// dialog, so make our own transaction and take care of that here.
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        android.support.v4.app.Fragment prev =
                getSupportFragmentManager().findFragmentByTag("dialog");
        if (prev != null) { ft.remove(prev); }
        ft.addToBackStack(null);
        dialogFragment.show(ft, "dialog");
    }

    private String getFilename(){
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath, AUDIO_RECORDER_FOLDER);

        if(!file.exists()){
            file.mkdirs();
        }

        return (file.getAbsolutePath() + "/" + System.currentTimeMillis() + file_exts[currentFormat]);
    }

    private void startRecording(){
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(output_formats[currentFormat]);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        audioUri = getFilename();
        recorder.setOutputFile(audioUri);
        recorder.setOnErrorListener(errorListener);
        recorder.setOnInfoListener(infoListener);

        try {
            recorder.prepare();
            recorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private MediaRecorder.OnErrorListener errorListener = new MediaRecorder.OnErrorListener() {
        @Override
        public void onError(MediaRecorder mr, int what, int extra) {
            Log.d("Error: ", what + ", " + extra);
        }
    };

    private MediaRecorder.OnInfoListener infoListener = new MediaRecorder.OnInfoListener() {
        @Override
        public void onInfo(MediaRecorder mr, int what, int extra) {
            Log.d("Warning: ", what + ", " + extra);
        }
    };

    private void stopRecording(){
        if(null != recorder){
            recorder.stop();
            recorder.reset();
            recorder.release();

            featureFlag = 3;
            //REQUEST_AUDIO_CAPTURE = 27;
            createAudioMessage(Uri.fromFile(new File(audioUri)));
            recorder = null;
        }
    }

}
