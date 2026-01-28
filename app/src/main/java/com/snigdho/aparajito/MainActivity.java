package com.snigdho.aparajito;

import android.graphics.Color;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.PermissionRequest;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import android.view.View;
import android.widget.FrameLayout;

public class MainActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView playerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Initialize the Native ExoPlayer (Bottom Layer)
        player = new ExoPlayer.Builder(this).build();
        playerView = findViewById(R.id.player_view);
        playerView.setPlayer(player);
        playerView.setUseController(false); // We use your Web UI for controls

        // 2. Setup the Transparent WebView (Top Layer)
        WebView webView = findViewById(R.id.webview);
        webView.setBackgroundColor(Color.TRANSPARENT); // Makes the web view see-through
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Handle permissions for PeerJS voice/video chat
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.grant(request.getResources());
            }
        });

        // 3. The Bridge: HTML calls these to control the Native Player
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void playNative(String uri) {
                runOnUiThread(() -> {
                    MediaItem mediaItem = MediaItem.fromUri(uri);
                    player.setMediaItem(mediaItem);
                    player.prepare();
                    player.play();
                });
            }

            @JavascriptInterface
            public void setVideoPosition(int x, int y, int width, int height) {
                runOnUiThread(() -> {
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) playerView.getLayoutParams();
                    params.width = width;
                    params.height = height;
                    params.leftMargin = x;
                    params.topMargin = y;
                    playerView.setLayoutParams(params);
                });
            }

            @JavascriptInterface
            public void controlVideo(String action) {
                runOnUiThread(() -> {
                    if (action.equals("play")) player.play();
                    else if (action.equals("pause")) player.pause();
                });
            }
        }, "AndroidInterface");

        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
        }
    }
}
