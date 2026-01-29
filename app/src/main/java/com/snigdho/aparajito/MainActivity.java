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
import android.view.WindowManager;
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
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import org.json.JSONObject;
import com.google.common.collect.ImmutableList;

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
            if (player != null && isNativeMode) {
                long current = player.getCurrentPosition();
                long total = player.getDuration();
                if (total > 0 && total != C.TIME_UNSET) {
                    webView.post(() -> webView.evaluateJavascript("updateNativeTimeline(" + current + ", " + total + ")", null));
                }
            }
            handler.postDelayed(this, 300);
        }
    };

    private final ActivityResultLauncher<String> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    if (mUploadMessage != null) mUploadMessage.onReceiveValue(new Uri[]{uri});
                    mUploadMessage = null;
                    runOnUiThread(() -> {
                        isNativeMode = true;
                        playerView.setVisibility(View.VISIBLE);
                        playNativeStream(uri.toString());
                        webView.evaluateJavascript("handleLocalFileSelection('" + uri.toString() + "')", null);
                    });
                } else if (mUploadMessage != null) {
                    mUploadMessage.onReceiveValue(null);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
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

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                webView.post(() -> webView.evaluateJavascript("setPlayState(" + isPlaying + ")", null));
            }
        });

        webView = findViewById(R.id.webview);
        webView.setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) { request.grant(request.getResources()); }
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                mUploadMessage = filePathCallback;
                filePickerLauncher.launch("video/*");
                return true;
            }
        });

        webView.addJavascriptInterface(new Object() {
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
                        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
                        hideSystemUI();
                        set.constrainPercentHeight(R.id.player_view, 1.0f);
                    } else {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
                        showSystemUI();
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
                    for (int i = 0; i < groups.size(); i++) {
                        Tracks.Group group = groups.get(i);
                        if (group.getType() == trackType) {
                            for (int j = 0; j < group.length; j++) {
                                Format format = group.getTrackFormat(j);
                                JSONObject obj = new JSONObject();
                                obj.put("groupIndex", i);
                                obj.put("trackIndex", j);
                                String label = (format.label != null) ? format.label : 
                                               (format.language != null ? format.language : (type + " " + (j + 1)));
                                obj.put("label", label);
                                obj.put("selected", group.isTrackSelected(j));
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
                        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(trackType, true).build());
                    } else {
                        Tracks tracks = player.getCurrentTracks();
                        Tracks.Group group = tracks.getGroups().get(groupIndex);
                        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                                .setTrackTypeDisabled(trackType, false)
                                .setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), trackIndex))
                                .build());
                    }
                });
            }
        }, "AndroidInterface");

        webView.loadUrl("file:///android_asset/index.html");
        handler.post(progressUpdater);
    }

    private void hideSystemUI() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void showSystemUI() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.show(WindowInsetsCompat.Type.systemBars());
    }

    private void playNativeStream(String uri) {
        player.setMediaItem(MediaItem.fromUri(uri));
        player.prepare();
        player.play();
    }

    @Override protected void onStop() { super.onStop(); if (player != null) player.pause(); }
    @Override protected void onDestroy() { super.onDestroy(); handler.removeCallbacks(progressUpdater); if (player != null) player.release(); }
}
