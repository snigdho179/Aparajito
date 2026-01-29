package com.snigdho.aparajito;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class MainActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;
    private WebView webView;
    private ValueCallback<Uri[]> mUploadMessage;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isNativeMode = false;

    // Timeline Updater
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying() && isNativeMode) {
                long current = player.getCurrentPosition();
                long total = player.getDuration();
                webView.evaluateJavascript("updateNativeTimeline(" + current + ", " + total + ")", null);
            }
            handler.postDelayed(this, 500);
        }
    };

    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    if (mUploadMessage != null) mUploadMessage.onReceiveValue(new Uri[]{uri});
                    mUploadMessage = null;
                    playNative(uri.toString());
                    webView.loadUrl("javascript:handleLocalFileSelection('" + uri.toString() + "')");
                } else {
                    if (mUploadMessage != null) mUploadMessage.onReceiveValue(null);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 1. FIX NOTCH & FULLSCREEN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setContentView(R.layout.activity_main);

        // 2. ASK FOR MIC PERMISSION
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 123);
        }

        // 3. Setup Native Player (Background Layer)
        player = new ExoPlayer.Builder(this).build();
        playerView = findViewById(R.id.player_view);
        playerView.setPlayer(player);
        playerView.setUseController(false);

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) handler.post(progressUpdater);
                else handler.removeCallbacks(progressUpdater);
                webView.evaluateJavascript("setPlayState(" + isPlaying + ")", null);
            }
        });

        // 4. Setup WebView (Foreground Layer - Transparent)
        webView = findViewById(R.id.webview);
        webView.setBackgroundColor(Color.TRANSPARENT); // Crucial for "Hybrid" feel
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) { request.grant(request.getResources()); }
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mUploadMessage != null) mUploadMessage.onReceiveValue(null);
                mUploadMessage = filePathCallback;
                filePickerLauncher.launch("video/*");
                return true;
            }
        });

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void playNative(String uri) {
                runOnUiThread(() -> {
                    isNativeMode = true;
                    playerView.setVisibility(View.VISIBLE);
                    playNative(uri);
                });
            }

            @JavascriptInterface
            public void hideNative() {
                runOnUiThread(() -> {
                    isNativeMode = false;
                    player.pause();
                    playerView.setVisibility(View.GONE);
                });
            }

            @JavascriptInterface
            public void control(String action, long value) {
                runOnUiThread(() -> {
                    if (action.equals("play")) player.play();
                    else if (action.equals("pause")) player.pause();
                    else if (action.equals("seek")) player.seekTo(value);
                });
            }

            @JavascriptInterface
            public void toggleRotation(boolean isLandscape) {
                runOnUiThread(() -> {
                    if (isLandscape) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        hideSystemUI();
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        showSystemUI();
                    }
                });
            }
            
            @JavascriptInterface
            public void changeTrack(String type) {
                runOnUiThread(() -> {
                    TrackSelectionParameters params = player.getTrackSelectionParameters();
                    if(type.equals("audio")) {
                        // Cycles audio tracks
                         player.setTrackSelectionParameters(params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).build());
                    } else if (type.equals("sub")) {
                        // Toggles subtitles
                        boolean isDisabled = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
                        player.setTrackSelectionParameters(params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isDisabled).build());
                    }
                });
            }
        }, "AndroidInterface");

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().getInsetsController().show(WindowInsets.Type.systemBars());
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void playNative(String uri) {
        MediaItem mediaItem = MediaItem.fromUri(uri);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }
}
