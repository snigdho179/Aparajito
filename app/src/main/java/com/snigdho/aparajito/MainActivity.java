package com.snigdho.aparajito;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
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

    // 1. Timeline Sync Engine
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying()) {
                long current = player.getCurrentPosition();
                long total = player.getDuration();
                webView.evaluateJavascript("updateTimeline(" + current + ", " + total + ")", null);
            }
            handler.postDelayed(this, 500);
        }
    };

    // 2. Modern File Picker (Android 13+ Compatible)
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
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // 3. Initialize Native Player
        player = new ExoPlayer.Builder(this).build();
        playerView = findViewById(R.id.player_view);
        playerView.setPlayer(player);
        playerView.setUseController(false); // HTML handles controls

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) handler.post(progressUpdater);
                else handler.removeCallbacks(progressUpdater);
                webView.evaluateJavascript("setPlayState(" + isPlaying + ")", null);
            }
        });

        // 4. Setup Transparent WebView
        webView = findViewById(R.id.webview);
        webView.setBackgroundColor(Color.TRANSPARENT);
        
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

        // 5. The "Pro" Bridge Interface
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void playNative(String uri) {
                runOnUiThread(() -> {
                    playerView.setVisibility(View.VISIBLE); // Hybrid: Show Native
                    playNative(uri);
                });
            }

            @JavascriptInterface
            public void hideNative() {
                runOnUiThread(() -> {
                    player.pause();
                    playerView.setVisibility(View.GONE); // Hybrid: Hide Native for YouTube
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
                    setRequestedOrientation(isLandscape ? 
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : 
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                });
            }
            
            @JavascriptInterface
            public void changeTrack(String type) {
                runOnUiThread(() -> {
                    TrackSelectionParameters params = player.getTrackSelectionParameters();
                    if(type.equals("audio")) {
                         // Fixes "No Audio" issues
                         player.setTrackSelectionParameters(params.buildUpon()
                                 .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).build());
                    } else if (type.equals("sub")) {
                        // FIXED: Correct way to toggle subtitles in Media3 1.2.1
                        boolean isDisabled = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT);
                        player.setTrackSelectionParameters(params.buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isDisabled).build());
                    }
                });
            }
        }, "AndroidInterface");

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void playNative(String uri) {
        MediaItem mediaItem = MediaItem.fromUri(uri);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }
}
