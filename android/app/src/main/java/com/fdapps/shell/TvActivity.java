package com.fdapps.shell;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

/** The TV channel (Google TV / Android TV): the site's TV page full screen, driven by the remote.
 *  Back walks the page's own history, and leaves the app from the first screen.
 *  Coming back after 10+ minutes away reloads, so the TV always runs the newest version.
 *  Uses only current Android APIs - nothing Google Play lists as deprecated for edge-to-edge. */
public class TvActivity extends Activity {
    private WebView web;
    private String home;
    private long pausedAt;
    private Object backCb;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String extra = getIntent() != null ? getIntent().getStringExtra("url") : null;
        home = extra != null ? extra : BuildConfig.TV_URL;

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) { return false; }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req.isForMainFrame()) showOffline();
            }
        });

        boolean tv = isTv();
        if (Build.VERSION.SDK_INT >= 30) {
            if (tv) {
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(WindowInsets.Type.systemBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }
            /* edge-to-edge: keep the page clear of the status bar, navigation bar and camera cut-out */
            web.setOnApplyWindowInsetsListener((View v, WindowInsets insets) -> {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return WindowInsets.CONSUMED;
            });
        }

        if (Build.VERSION.SDK_INT >= 33) {
            OnBackInvokedCallback cb = this::goBack;
            backCb = cb;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        }

        web.loadUrl(home);
        web.requestFocus();
    }

    private void goBack() {
        if (web != null && web.canGoBack()) web.goBack(); else finish();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        goBack();
    }

    private void showOffline() {
        String b = BuildConfig.BRAND;
        String html = "<html><body style=\"margin:0;background:#000;color:#fff;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center\">"
                + "<div><div style=\"font-size:40px;font-weight:800\">" + b + "</div>"
                + "<div style=\"font-size:24px;margin:24px 0;color:#c9d1dd\">Can't reach " + b + " right now. Check the internet connection.</div>"
                + "<button id=r autofocus style=\"font-size:26px;padding:14px 40px;border-radius:30px;border:3px solid #7cc7ff;background:#0b1426;color:#fff\">Try again</button></div>"
                + "<script>var u=" + jsString(home) + ";document.getElementById('r').onclick=function(){location.replace(u)};"
                + "addEventListener('keydown',function(e){if(e.key==='Enter'){location.replace(u)}});</script></body></html>";
        web.loadDataWithBaseURL("https://localhost/", html, "text/html", "utf-8", null);
    }

    private static String jsString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private boolean isTv() {
        UiModeManager m = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        return m != null && m.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    @Override
    protected void onPause() {
        super.onPause();
        pausedAt = SystemClock.elapsedRealtime();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web == null) return;
        web.onResume();
        if (pausedAt > 0 && SystemClock.elapsedRealtime() - pausedAt > 10 * 60 * 1000L) web.loadUrl(home);
        pausedAt = 0;
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCb != null) getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) backCb);
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
