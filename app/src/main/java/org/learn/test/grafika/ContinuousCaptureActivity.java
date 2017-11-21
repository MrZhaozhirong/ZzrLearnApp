package org.learn.test.grafika;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.learn.test.R;
import org.learn.test.grafika.component.CircularEncoder;
import org.learn.test.grafika.gles.EglCore;
import org.learn.test.grafika.gles.FullFrameRect;
import org.learn.test.grafika.gles.Texture2dProgram;
import org.learn.test.grafika.gles.WindowSurface;
import org.learn.test.grafika.util.AspectFrameLayout;
import org.learn.test.grafika.util.CameraUtils;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;



/**
 * Created by zzr on 2017/11/14.
 */

public class ContinuousCaptureActivity extends Activity implements SurfaceHolder.Callback {

    private static final String TAG = GrafikaMainActivity.TAG;
    private static final int VIDEO_WIDTH = 720;  // dimensions for 720p video
    private static final int VIDEO_HEIGHT = 1280;
    private static final int DESIRED_PREVIEW_FPS = 15;

    private MainHandler mHandler;

    private static class MainHandler extends Handler implements CircularEncoder.Callback {
        private WeakReference<ContinuousCaptureActivity> mWeakActivity;
        public static final int MSG_BLINK_TEXT = 0;
        public static final int MSG_FRAME_AVAILABLE = 1;
        public static final int MSG_FILE_SAVE_COMPLETE = 2;
        public static final int MSG_BUFFER_STATUS = 3;

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
                    break;
                }
                case MSG_FRAME_AVAILABLE: {
                    activity.drawFrame();
                    break;
                }
                case MSG_BUFFER_STATUS: {
                    long duration = (((long) msg.arg1) << 32) |
                            (((long) msg.arg2) & 0xffffffffL);
                    activity.updateBufferStatus(duration);
                    break;
                }
                case MSG_FILE_SAVE_COMPLETE: {
                    activity.fileSaveComplete(msg.arg1);
                    break;
                }
                default:
                    throw new RuntimeException("Unknown message " + msg.what);
            }
        }

        @Override
        public void fileSaveComplete(int status) {
            sendMessage(obtainMessage(MSG_FILE_SAVE_COMPLETE, status, 0, null));
        }

        @Override
        public void bufferStatus(long totalTimeMsec) {
            sendMessage(obtainMessage(MSG_BUFFER_STATUS,
                    (int) (totalTimeMsec >> 32), (int) totalTimeMsec));
        }
    }


    private Camera mCamera;
    private int mCameraPreviewThousandFps;
    private File mOutputFile;
    private float mSecondsOfVideo;
    private boolean mFileSaveInProgress;
    SurfaceView sv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.grafika_continuous_capture);

        sv = (SurfaceView) findViewById(R.id.continuousCapture_surfaceView);
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

        boolean wantEnabled = (mCircEncoder != null) && !mFileSaveInProgress;
        Button button = (Button) findViewById(R.id.capture_button);
        if (button.isEnabled() != wantEnabled) {
            Log.d(TAG, "setting enabled = " + wantEnabled);
            button.setEnabled(wantEnabled);
        }
    }

    /**
     * Updates the buffer status UI.
     */
    private void updateBufferStatus(long durationUsec) {
        mSecondsOfVideo = durationUsec / 1000000.0f;
        updateControls();
    }

    /**
     * The file save has completed.  We can resume recording.
     */
    private void fileSaveComplete(int status) {
        Log.d(TAG, "fileSaveComplete " + status);
        if (!mFileSaveInProgress) {
            throw new RuntimeException("WEIRD: got fileSaveCmplete when not in progress");
        }
        mFileSaveInProgress = false;
        updateControls();
        TextView tv = (TextView) findViewById(R.id.recording_text);
        String str = getString(R.string.nowRecording);
        tv.setText(str);
        if (status == 0) {
            str = getString(R.string.recordingSucceeded);
        } else {
            str = getString(R.string.recordingFailed, status);
        }
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
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

        if (mCircEncoder != null) {
            mCircEncoder.shutdown();
            mCircEncoder = null;
        }
        if (mCameraTexture != null) {
            mCameraTexture.release();
            mCameraTexture = null;
        }
        if (mDisplaySurface != null) {
            mDisplaySurface.release();
            mDisplaySurface = null;
        }
        if (mFullFrameBlit != null) {
            mFullFrameBlit.release(false);
            mFullFrameBlit = null;
        }
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
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



    // Handles onClick for "capture" button.
    public void clickCapture(@SuppressWarnings("unused") View unused) {
        Log.d(TAG, "capture");
        if (mFileSaveInProgress) {
            Log.w(TAG, "HEY: file save is already in progress");
            return;
        }

        boolean isSaving = mCircEncoder.saveVideo(mOutputFile);
        if(isSaving){
            mFileSaveInProgress = true;
            updateControls();
            TextView tv = (TextView) findViewById(R.id.recording_text);
            String str = getString(R.string.nowSaving);
            tv.setText(str);
        }
    }



    private EglCore mEglCore;
    private WindowSurface mDisplaySurface;
    private SurfaceTexture mCameraTexture;  // receives the output from the camera preview
    private FullFrameRect mFullFrameBlit;
    private int mTextureId;
    private int mFrameNum;
    private final float[] mTmpMatrix = new float[16];
    private WindowSurface mEncoderSurface;
    private CircularEncoder mCircEncoder;

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated holder=" + holder);
        // Set up everything that requires an EGL context.
        // We had to wait until we had a surface because you can't make an EGL context current without one,
        // and creating a temporary 1x1 pbuffer is a waste of time.
        // The display surface that we use for the SurfaceView, and the encoder surface we use for video,
        // use the same EGL context.
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
        mDisplaySurface = new WindowSurface(mEglCore, holder.getSurface(), false);
        mDisplaySurface.makeCurrent();

        mFullFrameBlit = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mTextureId = mFullFrameBlit.createTextureObject();
        mCameraTexture = new SurfaceTexture(mTextureId);
        mCameraTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                //Log.d(TAG, "frame available");
                mHandler.sendEmptyMessage(MainHandler.MSG_FRAME_AVAILABLE);
            }
        });

        try {
            Log.d(TAG, "starting camera preview");
            mCamera.setPreviewTexture(mCameraTexture);
            mCamera.startPreview();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        // Note: adjust bit rate based on frame rate?
        // Note: adjust video width/height based on what we're getting from the camera preview?
        //       (can we guarantee that camera preview size is compatible with AVC video encoder?)
        try {
            mCircEncoder = new CircularEncoder(VIDEO_WIDTH, VIDEO_HEIGHT, 6000000,
                    mCameraPreviewThousandFps / 1000, 7, mHandler);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mEncoderSurface = new WindowSurface(mEglCore, mCircEncoder.getInputSurface(), true);
        updateControls();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height + " holder=" + holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed holder=" + holder);
    }



    /**
     * Draws a frame onto the SurfaceView and the encoder surface.
     * <p>
     * This will be called whenever we get a new preview frame from the camera.  This runs
     * on the UI thread, which ordinarily isn't a great idea -- you really want heavy work
     * to be on a different thread -- but we're really just throwing a few things at the GPU.
     * The upside is that we don't have to worry about managing state changes between threads.
     * <p>
     * If there was a pending frame available notification when we shut down,
     * we might get here after onPause().
     */
    private void drawFrame() {
        if (mEglCore == null) {
            Log.d(TAG, "Skipping drawFrame after shutdown");
            return;
        }
        // Latch the next frame from the camera.
        mDisplaySurface.makeCurrent();
        mCameraTexture.updateTexImage();
        mCameraTexture.getTransformMatrix(mTmpMatrix);
        //Log.w(TAG, mTmpMatrix[0]+" "+mTmpMatrix[4]+" "+mTmpMatrix[8]+" "+mTmpMatrix[12]);
        //Log.w(TAG, mTmpMatrix[1]+" "+mTmpMatrix[5]+" "+mTmpMatrix[9]+" "+mTmpMatrix[13]);
        //Log.w(TAG, mTmpMatrix[2]+" "+mTmpMatrix[6]+" "+mTmpMatrix[10]+" "+mTmpMatrix[14]);
        //Log.w(TAG, mTmpMatrix[3]+" "+mTmpMatrix[7]+" "+mTmpMatrix[11]+" "+mTmpMatrix[15]);
        // Fill the SurfaceView with it.
        int viewWidth = sv.getWidth();
        int viewHeight = sv.getHeight();
        GLES20.glViewport(0, 0, viewWidth, viewHeight);
        mFullFrameBlit.drawFrame(mTextureId, mTmpMatrix);
        //drawExtra(mFrameNum, viewWidth, viewHeight);
        mDisplaySurface.swapBuffers();

        // Send it to the video encoder.
        if (!mFileSaveInProgress) {
            mEncoderSurface.makeCurrent();
            GLES20.glViewport(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);
            mFullFrameBlit.drawFrame(mTextureId, mTmpMatrix);
            drawExtra(mFrameNum, VIDEO_WIDTH, VIDEO_HEIGHT);
            mEncoderSurface.setPresentationTime(mCameraTexture.getTimestamp());
            mCircEncoder.frameAvailableSoon();
            mEncoderSurface.swapBuffers();
        }

        mFrameNum++;
    }

    // Adds a bit of extra stuff to the display just to give it flavor.
    private static void drawExtra(int frameNum, int width, int height) {
        // We "draw" with the scissor rect and clear calls.  Note this uses window coordinates.
        int val = frameNum % 3;
        switch (val) {
            case 0:  GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);   break;
            case 1:  GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);   break;
            case 2:  GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);   break;
        }

        int xpos = (int) (width * ((frameNum % 100) / 100.0f));
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(xpos, 0, width / 32, height / 32);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }
}
