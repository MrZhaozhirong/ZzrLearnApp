package org.learn.test.grafika;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.learn.test.R;
import org.learn.test.grafika.util.AspectFrameLayout;
import org.learn.test.grafika.util.CameraUtils;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * Created by zzr on 2017/11/14.
 */

public class ContinuousCaptureActivity extends Activity implements SurfaceHolder.Callback {

    private static final String TAG = GrafikaMainActivity.TAG;
    private static final int VIDEO_WIDTH = 1280;  // dimensions for 720p video
    private static final int VIDEO_HEIGHT = 720;
    private static final int DESIRED_PREVIEW_FPS = 15;

    private MainHandler mHandler;
    private static class MainHandler extends Handler {
        private WeakReference<ContinuousCaptureActivity> mWeakActivity;
        public static final int MSG_BLINK_TEXT = 0;

        public MainHandler(ContinuousCaptureActivity activity) {
            mWeakActivity = new WeakReference<ContinuousCaptureActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ContinuousCaptureActivity activity = mWeakActivity.get();
            if (activity == null) {
                Log.d(TAG, "Got message for dead activity");
                return;
            }

            switch (msg.what) {
                case MSG_BLINK_TEXT: {
                    TextView tv = (TextView) activity.findViewById(R.id.recording_text);
                    // Attempting to make it blink by using setEnabled() doesn't work --
                    // it just changes the color.  We want to change the visibility.
                    int visibility = tv.getVisibility();
                    if (visibility == View.VISIBLE) {
                        visibility = View.INVISIBLE;
                    } else {
                        visibility = View.VISIBLE;
                    }
                    tv.setVisibility(visibility);
                    int delay = (visibility == View.VISIBLE) ? 1000 : 200;
                    sendEmptyMessageDelayed(MSG_BLINK_TEXT, delay);
                }break;
            }
        }
    }



    private Camera mCamera;
    private int mCameraPreviewThousandFps;
    private File mOutputFile;
    private float mSecondsOfVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.grafika_continuous_capture);

        SurfaceView sv = (SurfaceView) findViewById(R.id.continuousCapture_surfaceView);
        SurfaceHolder sh = sv.getHolder();
        sh.addCallback(this);

        mHandler = new MainHandler(this);
        mHandler.sendEmptyMessageDelayed(MainHandler.MSG_BLINK_TEXT, 1500);

        mOutputFile = new File(getFilesDir(), "continuous-capture.mp4");
        mSecondsOfVideo = 0.0f;
        updateControls();
    }

    /**
     * Updates the current state of the controls.
     */
    private void updateControls() {
        String str = getString(R.string.secondsOfVideo, mSecondsOfVideo);
        TextView tv = (TextView) findViewById(R.id.capturedVideoDesc_text);
        tv.setText(str);

         boolean wantEnabled = false;//(mCircEncoder != null) && !mFileSaveInProgress;
        Button button = (Button) findViewById(R.id.capture_button);
        if (button.isEnabled() != wantEnabled) {
            Log.d(TAG, "setting enabled = " + wantEnabled);
            button.setEnabled(wantEnabled);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ideally, the frames from the camera are at the same resolution as the input to
        // the video encoder so we don't have to scale.
        openCamera(VIDEO_WIDTH, VIDEO_HEIGHT, DESIRED_PREVIEW_FPS);
    }

    private void openCamera(int desiredWidth, int desiredHeight, int desiredFps) {
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized");
        }

        Camera.CameraInfo info = new Camera.CameraInfo();
        // Try to find a front-facing camera (e.g. for videoconferencing).
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mCamera = Camera.open(i);
                break;
            }
        }
        if (mCamera == null) {
            Log.d(TAG, "No front-facing camera found; opening default");
            mCamera = Camera.open();    // opens first back-facing camera
        }
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }

        Camera.Parameters parms = mCamera.getParameters();
        CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);
        // Try to set the frame rate to a constant value.
        mCameraPreviewThousandFps = CameraUtils.chooseFixedPreviewFps(parms, desiredFps * 1000);
        // Give the camera a hint that we're recording video.
        // This can have a big impact on frame rate.
        parms.setRecordingHint(true);

        mCamera.setParameters(parms);

        Camera.Size cameraPreviewSize = parms.getPreviewSize();
        String previewFacts = cameraPreviewSize.width + "x" + cameraPreviewSize.height +
                " @" + (mCameraPreviewThousandFps / 1000.0f) + "fps";
        Log.d(TAG, "Camera config: " + previewFacts);

        // Set the preview aspect ratio.
        AspectFrameLayout layout = (AspectFrameLayout) findViewById(R.id.continuousCapture_afl);
        layout.setAspectRatio((double) cameraPreviewSize.width / cameraPreviewSize.height);
    }


    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    /**
     * Stops camera preview, and releases the camera to the system.
     */
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            Log.d(TAG, "releaseCamera -- done");
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height + " holder=" + holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed holder=" + holder);
    }
}
