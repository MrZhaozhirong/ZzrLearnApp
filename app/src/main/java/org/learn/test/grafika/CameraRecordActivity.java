package org.learn.test.grafika;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import org.learn.test.R;
import org.learn.test.grafika.component.CameraRecordEncoder;
import org.learn.test.grafika.component.CameraRecordHandler;
import org.learn.test.grafika.component.CameraRecordRenderer;
import org.learn.test.grafika.util.AspectFrameLayout;
import org.learn.test.grafika.util.CameraUtils;

import java.io.File;
import java.io.IOException;

/**
 * Created by zzr on 2017/12/14.
 */

/**
 * 在屏幕上显示相机预览，同时将其记录到MP4文件中。
 * 每次我们从相机接收到帧时，我们需要：
 * * 渲染帧到SurfaceView，在GLSurfaceView的渲染线程。
 * * 渲染帧到MediaCodec的Surface输入源，在编码器线，如果记录启用。
 *
 * 任何时候都有四件事在动：
 * 1、该活动所体现的UI线程。我们必须尊重应用程序生命周期的变化。特别是，
 *    我们需要释放和重新获得相机，如果用户切换远离我们，我们不能阻止另一个应用程序使用相机。
 * 2、相机在不停的产生预览帧，我们会处理成 SurfaceTexture。我们将会在主UI线程获取的到通知，
 *    除非我们在创建SurfaceTexture的线程上自定义了新的Looper
 * 3、视频编码器线程，体现在TextureMovieEncoder。这需要与GLSurfaceView的渲染器一同分享
 *    相机提供的 external格式的纹理，这代表着在这个线程的EGLContext
 *    需要在渲染器线程上下文中创建一个引用，
 * 4、GLSurfaceView的渲染器线程，体现在CameraSurfaceRenderer这个类中。
 *    我们不会获取得到暂停/恢复 或者是 线程的开启/停止的回调，虽然我们可以从Activity中
 *    在大多数情况发送自己的消息。   在这个线程上创建的EGLContext 必须与视频编码器共享
 *    而且必须用于创建一个相机需要使用的SurfaceTexture，作为SurfaceTexture的创建者，
 *    这也必须是调用的updateTexImage()的唯一者。因此，渲染线程是一个
 *    比较棘手但至少我们可以控制的 多线程的网络中心，
 *
 * 在这里，GLSurfaceView是相当痛苦的，最理想的是我们在创建视频编码器的时候，
 * 为它创建一个EGLContext，并传递到GLSurfaceView分享。API不允许这样做，
 * 所以我们必须反过来处理它。当GLSurfaceView被拆除（譬如，我们旋转的装置）
 * 该EGLContext扔了，这意味着它回来的时候我们必须重新创建 视频编码器使用的EGLContext。
 * （“暂停时保留EGLContext”功能没有帮助。）
 *
 * 我们可以利用TextureView代替GLSurfaceView简化这一点，但这会带来性能的打击，
 * 我们也可以渲染线程直接驱动的视频编码器，使他们能够从一个单一的EGLContext工作
 * 但是去耦操作是有用的，而且在你的UI线程上执行磁盘I/O通常是不明智的。
 *
 * 我们要从UI线程访问摄像头（安装、拆卸）和渲染线程（配置表面，开始预览），但API说你
 * 只能单个线程访问对象。因此，我们需要选择一个线程来拥有它，而另一个线程必须远程访问它。
 * 如果我们让渲染器线程管理它，事情变简单了，但我们需要肯定，摄像机在我们onPause的时候
 * 已经被释放，这意味着我们需要从UI线程同步调用到渲染线程，我们并没有完全控制住的线程。
 * 这不可怕，UI线程控制摄像机，通过Handler的机制，我们在UI线程上也有了渲染器的回调
 *
 * (这链接"http://developer.android.com/training/camera/cameradirect.html#TaskOpenCamera"
 * 推荐从非UI线程访问摄像机,避免将UI线程宕机。
 * 由于GLSurfaceView管理渲染线程不是一个好的选择，我们可能希望创建一个专用摄像机线。
 * 所以，这里不参考这种做法。)
 *
 * 随着三个线程同时工作（加上照相机导致帧到达时的周期性事件），我们在通信状态变化时必须非常小心。
 * 一般来说，我们希望向线程发送消息，而不是直接访问对象中的状态。
 *
 * 为了稍微使用API，视频编码器需要在活动重启后生存。在当前的实现中，
 * 它停止录制，但不会阻止时间的前进，因此您将看到视频中的暂停。
 * （我们可以调整计时器使其无缝，或者输出一个“暂停”消息，并在录制视频中保留它，
 * 或者让摄像机运行，以便在活动暂停时继续生成预览帧。）视频编码器对象被管理为Activity的静态属性。
 */
public class CameraRecordActivity extends Activity implements AdapterView.OnItemSelectedListener,
        SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = GrafikaMainActivity.TAG;
    private static final boolean VERBOSE = false;

    private GLSurfaceView mGLView;
    private Camera mCamera;
    private CameraRecordHandler mCameraHandler;
    private CameraRecordRenderer mRenderer;
    // this is static so it survives activity restarts
    private static CameraRecordEncoder sVideoEncoder = new CameraRecordEncoder();

    private boolean mRecordingEnabled;      // controls button state
    private int mCameraPreviewWidth, mCameraPreviewHeight;
    // Camera filters; must match up with cameraFilterNames in strings.xml
    public static final int FILTER_NONE = 0;
    public static final int FILTER_BLACK_WHITE = 1;
    public static final int FILTER_BLUR = 2;
    public static final int FILTER_SHARPEN = 3;
    public static final int FILTER_EDGE_DETECT = 4;
    public static final int FILTER_EMBOSS = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.grafika_camera_capture);

        // getFilesDir()
        // Environment.getExternalStorageDirectory().getPath()
        File outputFile = new File(Environment.getExternalStorageDirectory().getPath(), "camera-test.mp4");
        TextView fileText = (TextView) findViewById(R.id.cameraOutputFile_text);
        fileText.setText(outputFile.toString());

        Spinner spinner = (Spinner) findViewById(R.id.cameraFilter_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.cameraFilterNames, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        // Define a handler that receives camera-control messages from other threads.  All calls
        // to Camera must be made on the same thread.  Note we create this before the renderer
        // thread, so we know the fully-constructed object will be visible.
        mCameraHandler = new CameraRecordHandler(this);

        // Configure the GLSurfaceView.
        // This will start the Renderer thread, with an appropriate EGL context.
        mGLView = (GLSurfaceView) findViewById(R.id.cameraPreview_surfaceView);
        mGLView.setEGLContextClientVersion(2);
        mRenderer = new CameraRecordRenderer(mCameraHandler, sVideoEncoder, outputFile);
        mGLView.setRenderer(mRenderer);
        mGLView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        Log.d(TAG, "onCreate complete: " + this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume -- acquiring camera");
        updateControls();
        openCamera(1280, 720);
        // updates mCameraPreviewWidth/Height landscape
        // SetDisplayOrientation 90degree and updates mCameraPreviewHeight/Width portrait
        AspectFrameLayout layout = (AspectFrameLayout) findViewById(R.id.cameraPreview_afl);
        layout.setAspectRatio((double) mCameraPreviewHeight / mCameraPreviewWidth );

        mGLView.onResume();
        mGLView.queueEvent(new Runnable() {
            @Override public void run() {
                mRenderer.setCameraPreviewSize(mCameraPreviewHeight, mCameraPreviewWidth);
                // mCameraPreviewHeight/Width portrait
                // mCameraPreviewWidth/Height landscape
            }
        });
        Log.d(TAG, "onResume complete: " + this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause -- releasing camera");
        releaseCamera();
        mGLView.queueEvent(new Runnable() {
            @Override public void run() {
                // Tell the renderer that it's about to be paused so it can clean up.
                mRenderer.notifyPausing();
            }
        });
        mGLView.onPause();
        Log.d(TAG, "onPause complete");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mCameraHandler.invalidateHandler();     // paranoia
    }



    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Spinner spinner = (Spinner) parent;
        final int filterNum = spinner.getSelectedItemPosition();

        Log.d(TAG, "onItemSelected: " + filterNum);
        mGLView.queueEvent(new Runnable() {
            @Override public void run() {
                // notify the renderer that we want to change the encoder's state
                mRenderer.changeFilterMode(filterNum);
            }
        });
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) { }






    // Updates the on-screen controls to reflect the current state of the app.
    private void updateControls() {
        Button toggleRelease = (Button) findViewById(R.id.toggleRecording_button);
        int id = mRecordingEnabled ?
                R.string.toggleRecordingOff : R.string.toggleRecordingOn;
        toggleRelease.setText(id);
        //CheckBox cb = (CheckBox) findViewById(R.id.rebindHack_checkbox);
        //cb.setChecked(TextureRender.sWorkAroundContextProblem);
    }

    /**
     * Opens a camera, and attempts to establish preview mode at the specified width and height.
     * Sets mCameraPreviewWidth and mCameraPreviewHeight to the actual width/height of the preview.
     */
    private void openCamera(int desiredWidth, int desiredHeight) {
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
        mCamera.setDisplayOrientation(90);

        Camera.Parameters parms = mCamera.getParameters();
        CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);
        // Give the camera a hint that we're recording video.
        // This can have a big impact on frame rate.
        parms.setRecordingHint(true);
        // leave the frame rate set to default
        mCamera.setParameters(parms);

        int[] fpsRange = new int[2];
        Camera.Size mCameraPreviewSize = parms.getPreviewSize();
        parms.getPreviewFpsRange(fpsRange);
        String previewFacts = mCameraPreviewSize.width + "x" + mCameraPreviewSize.height;
        if (fpsRange[0] == fpsRange[1]) {
            previewFacts += " @" + (fpsRange[0] / 1000.0) + "fps";
        } else {
            previewFacts += " @[" + (fpsRange[0] / 1000.0) +
                    " - " + (fpsRange[1] / 1000.0) + "] fps";
        }
        TextView text = (TextView) findViewById(R.id.cameraParams_text);
        text.setText(previewFacts);

        mCameraPreviewWidth = mCameraPreviewSize.width;
        mCameraPreviewHeight = mCameraPreviewSize.height;
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

    /**
     * onClick handler for "record" button.
     */
    public void clickToggleRecording(@SuppressWarnings("unused") View unused) {
        mRecordingEnabled = !mRecordingEnabled;
        mGLView.queueEvent(new Runnable() {
            @Override public void run() {
                // notify the renderer that we want to change the encoder's state
                mRenderer.changeRecordingState(mRecordingEnabled);
            }
        });
        updateControls();
    }

    /**
     * Connects the SurfaceTexture to the Camera preview output, and starts the preview.
     */
    public void handleSetSurfaceTexture(SurfaceTexture st) {
        if (VERBOSE) Log.i(TAG, "SurfaceTexture "+st+" setOnFrameAvailableListener");
        st.setOnFrameAvailableListener(this);
        try {
            mCamera.setPreviewTexture(st);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mCamera.startPreview();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        // The SurfaceTexture uses this to signal the availability of a new frame.  The
        // thread that "owns" the external texture associated with the SurfaceTexture (which,
        // by virtue of the context being shared, *should* be either one) needs to call
        // updateTexImage() to latch the buffer.
        //
        // Once the buffer is latched, the GLSurfaceView thread can signal the encoder thread.
        // This feels backward -- we want recording to be prioritized over rendering -- but
        // since recording is only enabled some of the time it's easier to do it this way.
        //
        // Since GLSurfaceView doesn't establish a Looper, this will *probably* execute on
        // the main UI thread.  Fortunately, requestRender() can be called from any thread,
        // so it doesn't really matter.
        if (VERBOSE) Log.i(TAG, "SurfaceTexture "+surfaceTexture+" onFrameAvailable");
        mGLView.requestRender(); //callback GLSurfaceView onDrawFrame.
    }
}





