package it.pgp.xfiles;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import java.io.Serializable;

public abstract class EffectActivity extends Activity {

    protected ActionBar b;

    protected void setActivityIcon(int resId) {
        if (b != null) b.setIcon(resId);
    }

    public static Object currentlyOnFocus;

    public static final DialogInterface.OnShowListener defaultDialogShowListener = d -> EffectActivity.currentlyOnFocus = d;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = getActionBar();
        overridePendingTransition(R.anim.fade_in,R.anim.fade_out);
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentlyOnFocus = this;
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(R.anim.fade_in,R.anim.fade_out);
        if(this instanceof MainActivity) currentlyOnFocus = null;
    }

    public static Serializable serviceParams;

    // useful for removing usage of parcelization (which prevents using lazy iterables as params)
    @Override
    public ComponentName startService(Intent service) {
        serviceParams = service.getSerializableExtra("params");
        service.putExtra("params",(Serializable)null);
        return super.startService(service);
    }
}
