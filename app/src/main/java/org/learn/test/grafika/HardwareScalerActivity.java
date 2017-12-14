package org.learn.test.grafika;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.Choreographer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.TextView;

import org.learn.test.R;
import org.learn.test.grafika.component.HSRenderThread;

/**
 * Created by zzr on 2017/11/23.
 */

public class HardwareScalerActivity extends Activity implements SurfaceHolder.Callback, Choreographer.FrameCallback {
    private static final String TAG = GrafikaMainActivity.TAG;
    // This used to have "a few thoughts about app life cycle and SurfaceView".
    // These are now at http://source.android.com/devices/graphics/architecture.html in Appendix B.
    //
    //
    // This Activity uses approach #2 (Surface-driven).
    private int mSelectedSize;
    private static final int SURFACE_SIZE_TINY = 0;
    private static final int SURFACE_SIZE_SMALL = 1;
    private static final int SURFACE_SIZE_MEDIUM = 2;
    private static final int SURFACE_SIZE_FULL = 3;

    private int mFullViewWidth;
    private int mFullViewHeight;
    private int[][] mWindowWidthHeight;
    private static final int[] SURFACE_DIM = new int[] { 64, 240, 480, -1 };
    private static final String[] SURFACE_LABEL = new String[] {
            "tiny", "small", "medium", "full"
    };
    // Rendering code runs on this thread.  The thread's life span is tied to the Surface.
    private HSRenderThread mRenderThread;
    private SurfaceView sv;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "HardwareScalerActivity: onCreate");
        setContentView(R.layout.grafika_hardware_scaler);

        mSelectedSize = SURFACE_SIZE_FULL;
        mFullViewWidth = mFullViewHeight = 512;     // want actual view size, but it's not avail
        mWindowWidthHeight = new int[SURFACE_DIM.length][2];
        updateControls();

        sv = (SurfaceView) findViewById(R.id.hardwareScaler_surfaceView);
        sv.getHolder().addCallback(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If we already have a Surface, we just need to resume the frame notifications.
        if (mRenderThread != null) {
            Log.d(TAG, "onResume re-hooking choreographer");
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // If the callback was posted, remove it.  This stops the notifications.  Ideally we
        // would send a message to the thread letting it know, so when it wakes up it can
        // reset its notion of when the previous Choreographer event arrived.
        Log.d(TAG, "onPause unhooking choreographer");
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated holder=" + holder);
        // Grab the view's width.  It's not available before now.
        Rect size = holder.getSurfaceFrame();
        mFullViewWidth = size.width();
        mFullViewHeight = size.height();
        // Configure our fixed-size values.
        // We want to configure it so that the narrowest dimension
        // (e.g. width when device is in portrait orientation) is equal to the
        // value in SURFACE_DIM, and the other dimension is sized to maintain the same
        // aspect ratio.
        float windowAspect = (float) mFullViewHeight / (float) mFullViewWidth;
        for (int i = 0; i < SURFACE_DIM.length; i++) {
            if (i == SURFACE_SIZE_FULL) {
                // special-case for full size
                mWindowWidthHeight[i][0] = mFullViewWidth;
                mWindowWidthHeight[i][1] = mFullViewHeight;
            } else if (mFullViewWidth < mFullViewHeight) {
                // portrait
                mWindowWidthHeight[i][0] = SURFACE_DIM[i];
                mWindowWidthHeight[i][1] = (int) (SURFACE_DIM[i] * windowAspect);
            } else {
                // landscape
                mWindowWidthHeight[i][0] = (int) (SURFACE_DIM[i] / windowAspect);
                mWindowWidthHeight[i][1] = SURFACE_DIM[i];
            }
        }
        // Some controls include text based on the view dimensions, so update now.
        updateControls();

        mRenderThread = new HSRenderThread(sv.getHolder());
        mRenderThread.setName("HardwareScaler GL render");
        mRenderThread.start();
        mRenderThread.waitUntilReady();

        HSRenderThread.RenderHandler rh = mRenderThread.getHandler();
        if (rh != null) {
            rh.sendSetFlatShading(mFlatShadingChecked);
            rh.sendSurfaceCreated();
        }

        // start the draw events
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + holder);

        HSRenderThread.RenderHandler rh = mRenderThread.getHandler();
        if (rh != null) {
            rh.sendSurfaceChanged(format, width, height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d(TAG, "surfaceDestroyed holder=" + holder);
        // We need to wait for the render thread to shut down before continuing because we
        // don't want the Surface to disappear out from under it mid-render.
        // The frame notifications will have been stopped back in onPause(), but there might have
        // been one in progress.
        HSRenderThread.RenderHandler rh = mRenderThread.getHandler();
        if (rh != null) {
            rh.sendShutdown();
            try {
                mRenderThread.join();
            } catch (InterruptedException ie) {
                // not expected
                throw new RuntimeException("join was interrupted", ie);
            }
        }
        mRenderThread = null;
        Log.d(TAG, "surfaceDestroyed complete");
    }


    @Override
    public void doFrame(long frameTimeNanos) {
        HSRenderThread.RenderHandler rh = mRenderThread.getHandler();
        if (rh != null) {
            Choreographer.getInstance().postFrameCallback(this);
            rh.sendDoFrame(frameTimeNanos);
        }
    }





    // onClick handler for radio buttons.
    public void onRadioButtonClicked(View view) {
        int newSize;

        RadioButton rb = (RadioButton) view;
        if (!rb.isChecked()) {
            Log.d(TAG, "Got click on non-checked radio button");
            return;
        }
        switch (rb.getId()) {
            case R.id.surfaceSizeTiny_radio:
                newSize = SURFACE_SIZE_TINY;
                break;
            case R.id.surfaceSizeSmall_radio:
                newSize = SURFACE_SIZE_SMALL;
                break;
            case R.id.surfaceSizeMedium_radio:
                newSize = SURFACE_SIZE_MEDIUM;
                break;
            case R.id.surfaceSizeFull_radio:
                newSize = SURFACE_SIZE_FULL;
                break;
            default:
                throw new RuntimeException("Click from unknown id " + rb.getId());
        }
        mSelectedSize = newSize;
        int[] wh = mWindowWidthHeight[newSize];
        // Update the Surface size.
        // This causes a "surface changed" event,
        // but does not destroy and re-create the Surface.
        SurfaceView sv = (SurfaceView) findViewById(R.id.hardwareScaler_surfaceView);
        SurfaceHolder sh = sv.getHolder();
        Log.d(TAG, "setting size to " + wh[0] + "x" + wh[1]);
        sh.setFixedSize(wh[0], wh[1]);
    }

    // Updates the on-screen controls to reflect the current state of the app.
    private boolean mFlatShadingChecked;
    private void updateControls() {
        configureRadioButton(R.id.surfaceSizeTiny_radio, SURFACE_SIZE_TINY);
        configureRadioButton(R.id.surfaceSizeSmall_radio, SURFACE_SIZE_SMALL);
        configureRadioButton(R.id.surfaceSizeMedium_radio, SURFACE_SIZE_MEDIUM);
        configureRadioButton(R.id.surfaceSizeFull_radio, SURFACE_SIZE_FULL);

        TextView tv = (TextView) findViewById(R.id.viewSizeValue_text);
        tv.setText(mFullViewWidth + "x" + mFullViewHeight);

        CheckBox cb = (CheckBox) findViewById(R.id.flatShading_checkbox);
        cb.setChecked(mFlatShadingChecked);
    }
    // Generates the radio button text.
    private void configureRadioButton(int id, int index) {
        RadioButton rb;
        rb = (RadioButton) findViewById(id);
        rb.setChecked(mSelectedSize == index);
        rb.setText(SURFACE_LABEL[index] + " (" + mWindowWidthHeight[index][0] + "x" +
                mWindowWidthHeight[index][1] + ")");
    }

    public void onFlatShadingClicked(@SuppressWarnings("unused") View unused) {
        CheckBox cb = (CheckBox) findViewById(R.id.flatShading_checkbox);
        mFlatShadingChecked = cb.isChecked();

        HSRenderThread.RenderHandler rh = mRenderThread.getHandler();
        if (rh != null) {
            rh.sendSetFlatShading(mFlatShadingChecked);
        }
    }
}
