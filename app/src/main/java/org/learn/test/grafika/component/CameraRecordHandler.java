package org.learn.test.grafika.component;

import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.learn.test.grafika.CameraRecordActivity;
import org.learn.test.grafika.GrafikaMainActivity;

import java.lang.ref.WeakReference;

/**
 * Created by zzr on 2017/12/15.
 */

public class CameraRecordHandler extends Handler {
    static final int MSG_SET_SURFACE_TEXTURE = 0xA;
    private static final String TAG = GrafikaMainActivity.TAG;

    // Weak reference to the Activity; only access this from the UI thread.
    private WeakReference<CameraRecordActivity> mWeakActivity;

    public CameraRecordHandler(CameraRecordActivity activity) {
        mWeakActivity = new WeakReference<CameraRecordActivity>(activity);
    }

    /**
     * Drop the reference to the activity.  Useful as a paranoid measure to ensure that
     * attempts to access a stale Activity through a handler are caught.
     */
    public void invalidateHandler() {
        mWeakActivity.clear();
    }

    @Override
    public void handleMessage(Message msg) {
        int what = msg.what;
        Log.d(TAG, "CameraCaptureHandler [" + this + "]: what=" + what);

        CameraRecordActivity activity = mWeakActivity.get();
        if (activity == null) {
            Log.w(TAG, "CameraCaptureHandler.handleMessage: activity is null");
            return;
        }

        switch (what) {
            case MSG_SET_SURFACE_TEXTURE:
                activity.handleSetSurfaceTexture((SurfaceTexture) msg.obj);
                break;
            default:
                super.handleMessage(msg);
        }
    }
}
