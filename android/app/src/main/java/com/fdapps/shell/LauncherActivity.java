package com.fdapps.shell;

import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;

import android.app.Activity;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.browser.trusted.TrustedWebActivityIntentBuilder;

/** Phone and tablet: opens the website full screen in the phone's browser engine (a Trusted Web Activity).
 *  On a TV it hands straight over to the TV screen. With no usable browser it falls back to the built-in web view. */
public class LauncherActivity extends Activity {
    private CustomTabsServiceConnection conn;
    private boolean launched;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (isTv()) { startActivity(new Intent(this, TvActivity.class)); finish(); return; }
        Uri data = getIntent() != null ? getIntent().getData() : null;
        final Uri uri = (data != null && "https".equals(data.getScheme())) ? data : Uri.parse(BuildConfig.PHONE_URL);
        String pkg = CustomTabsClient.getPackageName(this, null);
        if (pkg == null) { fallback(uri); return; }
        conn = new CustomTabsServiceConnection() {
            @Override
            public void onCustomTabsServiceConnected(ComponentName name, CustomTabsClient client) {
                if (launched) return;
                launched = true;
                CustomTabsSession session = client.newSession(null);
                if (session == null) { fallback(uri); return; }
                CustomTabColorSchemeParams colors = new CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(BuildConfig.BG).setNavigationBarColor(BuildConfig.BG).build();
                try {
                    new TrustedWebActivityIntentBuilder(uri).setDefaultColorSchemeParams(colors)
                            .build(session).launchTrustedWebActivity(LauncherActivity.this);
                    finish();
                } catch (Exception e) {
                    fallback(uri);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) { }
        };
        if (!CustomTabsClient.bindCustomTabsService(this, pkg, conn)) { conn = null; fallback(uri); }
    }

    private void fallback(Uri uri) {
        Intent i = new Intent(this, TvActivity.class);
        i.putExtra("url", uri.toString());
        startActivity(i);
        finish();
    }

    private boolean isTv() {
        UiModeManager m = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        return m != null && m.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    @Override
    protected void onDestroy() {
        if (conn != null) { try { unbindService(conn); } catch (Exception ignored) { } conn = null; }
        super.onDestroy();
    }
}
