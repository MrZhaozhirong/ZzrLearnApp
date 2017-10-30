package org.learn.test.ndkgl;

import android.app.Activity;
import android.os.Bundle;

/**
 * Created by zzr on 2017/10/30.
 */

public class NDKGLActivity extends Activity {

    static {
        System.loadLibrary("opengl-lib");
    }
    NDKGLView ndkglView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ndkglView = new NDKGLView(getApplicationContext());
        setContentView(ndkglView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ndkglView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ndkglView.onResume();
    }
}
