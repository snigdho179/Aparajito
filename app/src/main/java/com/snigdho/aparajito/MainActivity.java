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
import android.view.WindowManager; // <--- ADDED MISSING IMPORT
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import com.google.common.collect.ImmutableList;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;
    private WebView webView;
    private ValueCallback<Uri[]> mUploadMessage;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isNativeMode = false;

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.isPlaying() && isNativeMode) {
                long current = player.getCurrentPosition();
                long total = player.getDuration();
                // Force update even if duration is weird, JS handles the rest
                if (total != C.TIME_UNSET && total > 0) {
                    webView.evaluateJavascript("updateNativeTimeline(" + current + ", " + total + ")", null);
                }
            }
            handler.postDelayed(this, 250);
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
        
        // Default: Portrait Mode (Bars Visible, Reserved Space)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = 
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 123);
        }

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
                    ConstraintLayout root = findViewById(R.id.main_root);
                    ConstraintSet set = new ConstraintSet();
                    set.clone(root);

                    if (isLandscape) {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                        hideSystemUI();
                        // Fullscreen: Remove reservation (video fills notch)
                        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
                        set.constrainPercentHeight(R.id.player_view, 1.0f);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        showSystemUI();
                        // Portrait: Add reservation (video below status bar)
                        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
                        set.constrainPercentHeight(R.id.player_view, 0.3f);
                    }
                    set.applyTo(root);
                });
            }
            
            @JavascriptInterface
            public String getTrackList(String type) {
                try {
                    int trackType = type.equals("audio") ? C.TRACK_TYPE_AUDIO : C.TRACK_TYPE_TEXT;
                    JSONArray jsonArray = new JSONArray();
                    Tracks tracks = player.getCurrentTracks();
                    ImmutableList<Tracks.Group> groups = tracks.getGroups();
                    
                    for (int gIndex = 0; gIndex < groups.size(); gIndex++) {
                        Tracks.Group group = groups.get(gIndex);
                        if (group.getType() == trackType) {
                            for (int tIndex = 0; tIndex < group.length; tIndex++) {
                                Format format = group.getTrackFormat(tIndex);
                                JSONObject obj = new JSONObject();
                                obj.put("groupIndex", gIndex);
                                obj.put("trackIndex", tIndex);
                                
                                String label = "Unknown";
                                if (format.label != null) label = format.label;
                                else if (format.language != null) label = format.language;
                                else label = "Track " + (tIndex + 1);
                                
                                obj.put("label", label);
                                obj.put("selected", group.isSelected());
                                jsonArray.put(obj);
                            }
                        }
                    }
                    return jsonArray.toString();
                } catch (Exception e) { return "[]"; }
            }

            @JavascriptInterface
            public void selectTrack(String type, int groupIndex, int trackIndex) {
                runOnUiThread(() -> {
                    int trackType = type.equals("audio") ? C.TRACK_TYPE_AUDIO : C.TRACK_TYPE_TEXT;
                    if (groupIndex == -1) {
                        player.setTrackSelectionParameters(
                            player.getTrackSelectionParameters().buildUpon()
                                .setTrackTypeDisabled(trackType, true).build());
                        return;
                    }
                    Tracks tracks = player.getCurrentTracks();
                    if (groupIndex < tracks.getGroups().size()) {
                        Tracks.Group group = tracks.getGroups().get(groupIndex);
                        player.setTrackSelectionParameters(
                            player.getTrackSelectionParameters().buildUpon()
                                .setTrackTypeDisabled(trackType, false)
                                .setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), trackIndex))
                                .build());
                    }
                });
            }

        }, "AndroidInterface");

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void hideSystemUI() {
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
    }

    private void showSystemUI() {
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
    }

    private void playNative(String uri) {
        MediaItem mediaItem = MediaItem.fromUri(uri);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) player.pause();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) player.release();
    }
}
