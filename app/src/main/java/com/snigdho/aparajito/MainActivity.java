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
import android.widget.LinearLayout;
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
    private WebView webViewOverlay, webViewChat;
    private FrameLayout videoWrapper;
    private LinearLayout mainContainer;
    private ValueCallback<Uri[]> mUploadMessage;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Timeline Updater
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                long current = player.getCurrentPosition();
                long total = player.getDuration();
                // Send to overlay WebView
                webViewOverlay.evaluateJavascript("updateTimeline(" + current + ", " + total + ")", null);
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
                    webViewOverlay.loadUrl("javascript:handleLocalFileSelection('" + uri.toString() + "')");
                } else {
                    if (mUploadMessage != null) mUploadMessage.onReceiveValue(null);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // FIX NOTCH GAP
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attrib = getWindow().getAttributes();
            attrib.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // FIX MIC PERMISSION
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        mainContainer = findViewById(R.id.main_container);
        videoWrapper = findViewById(R.id.video_wrapper);

        // 1. Native Player
        player = new ExoPlayer.Builder(this).build();
        playerView = findViewById(R.id.player_view);
        playerView.setPlayer(player);
        playerView.setUseController(false);

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) handler.post(progressUpdater);
                else handler.removeCallbacks(progressUpdater);
                webViewOverlay.evaluateJavascript("setPlayState(" + isPlaying + ")", null);
            }
        });

        // 2. Setup Overlay WebView (Controls)
        webViewOverlay = findViewById(R.id.webview_video_overlay);
        setupWebView(webViewOverlay);
        webViewOverlay.addJavascriptInterface(new AndroidBridge(), "AndroidInterface");
        webViewOverlay.loadUrl("file:///android_asset/index.html"); // Loads controls UI

        // 3. Setup Chat WebView (Bottom)
        webViewChat = findViewById(R.id.webview_chat);
        setupWebView(webViewChat);
        webViewChat.addJavascriptInterface(new AndroidBridge(), "AndroidInterface");
        webViewChat.loadUrl("file:///android_asset/index.html"); // Loads chat UI
    }

    private void setupWebView(WebView wv) {
        wv.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        wv.setWebChromeClient(new WebChromeClient() {
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
    }

    private void playNative(String uri) {
        MediaItem mediaItem = MediaItem.fromUri(uri);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    // Bridge Class
    public class AndroidBridge {
        @JavascriptInterface
        public void playNative(String uri) {
            runOnUiThread(() -> {
                playerView.setVisibility(View.VISIBLE);
                playNative(uri);
            });
        }
        @JavascriptInterface
        public void hideNative() {
            runOnUiThread(() -> {
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
                    // Hide Chat, Maximize Video
                    webViewChat.setVisibility(View.GONE);
                    videoWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 
                        LinearLayout.LayoutParams.MATCH_PARENT
                    ));
                    // Hide System Bars (Immersive)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        getWindow().getInsetsController().hide(WindowInsets.Type.systemBars());
                    } else {
                        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                    }
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    // Show Chat, Restore Video Height
                    webViewChat.setVisibility(View.VISIBLE);
                    videoWrapper.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 
                        0, 0.4f // 40% height
                    ));
                    // Show System Bars
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        getWindow().getInsetsController().show(WindowInsets.Type.systemBars());
                    } else {
                        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
                    }
                }
            });
        }
        @JavascriptInterface
        public void changeTrack(String type) {
            runOnUiThread(() -> {
                TrackSelectionParameters params = player.getTrackSelectionParameters();
                if(type.equals("audio")) {
                        player.setTrackSelectionParameters(params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).build());
                } else if (type.equals("sub")) {
                    boolean isDisabled = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
                    player.setTrackSelectionParameters(params.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isDisabled).build());
                }
            });
        }
    }
}
