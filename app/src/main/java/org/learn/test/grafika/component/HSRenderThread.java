package org.learn.test.grafika.component;

import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.learn.test.grafika.gles.Drawable2d;
import org.learn.test.grafika.gles.EglCore;
import org.learn.test.grafika.gles.FlatShadedProgram;
import org.learn.test.grafika.gles.GeneratedTexture;
import org.learn.test.grafika.gles.GlUtil;
import org.learn.test.grafika.gles.Sprite2d;
import org.learn.test.grafika.gles.Texture2dProgram;
import org.learn.test.grafika.gles.WindowSurface;

import java.lang.ref.WeakReference;

import static org.learn.test.grafika.GrafikaMainActivity.TAG;

/**
 * Created by zzr on 2017/11/24.
 */

public class HSRenderThread extends Thread {
    // Object must be created on render thread to get correct Looper, but is used from
    // UI thread, so we need to declare it volatile to ensure the UI thread sees a fully
    // constructed object.
    private volatile RenderHandler mHandler;

    public RenderHandler getHandler() {
        return mHandler;
    }

    // Used to wait for the thread to start.
    private final Object mStartLock = new Object();
    private boolean mReady = false;

    private volatile SurfaceHolder mSurfaceHolder;  // contents may be updated by UI thread
    private EglCore mEglCore;
    private WindowSurface mWindowSurface;
    private FlatShadedProgram mFlatProgram;
    private Texture2dProgram mTexProgram;

    private final float[] mIdentityMatrix;
    private final Drawable2d mTriDrawable = new Drawable2d(Drawable2d.Prefab.TRIANGLE);
    private final Drawable2d mRectDrawable = new Drawable2d(Drawable2d.Prefab.RECTANGLE);
    private int mCoarseTexture;
    private int mFineTexture;

    // One spinning triangle, one bouncing rectangle, and four edge-boxes.
    private Sprite2d mTri;
    private Sprite2d mRect;
    private float mRectVelX, mRectVelY;     // velocity, in viewport units per second
    private Sprite2d mEdges[];
    private float mInnerLeft, mInnerTop, mInnerRight, mInnerBottom;
    // Orthographic projection matrix.
    private float[] mDisplayProjectionMatrix = new float[16];

    private boolean mUseFlatShading;
    private void setFlatShading(boolean useFlatShading) {
        mUseFlatShading = useFlatShading;
    }

    // Pass in the SurfaceView's SurfaceHolder.  Note the Surface may not yet exist.
    public HSRenderThread(SurfaceHolder holder) {
        mSurfaceHolder = holder;
        mIdentityMatrix = new float[16];
        Matrix.setIdentityM(mIdentityMatrix, 0);

        mTri = new Sprite2d(mTriDrawable);
        mRect = new Sprite2d(mRectDrawable);
        mEdges = new Sprite2d[4];
        for (int i = 0; i < mEdges.length; i++) {
            mEdges[i] = new Sprite2d(mRectDrawable);
        }
    }

    @Override
    public void run() {
        Looper.prepare();
        mHandler = new RenderHandler(this);
        mEglCore = new EglCore(null, 0);
        synchronized (mStartLock) {
            mReady = true;
            mStartLock.notify();    // signal waitUntilReady()
        }
        Looper.loop();

        Log.d(TAG, "looper quit");
        releaseGl();
        mEglCore.release();

        synchronized (mStartLock) {
            mReady = false;
        }
    }

    /**
     * Waits until the render thread is ready to receive messages.
     * Call from the UI thread.
     */
    public void waitUntilReady() {
        synchronized (mStartLock) {
            while (!mReady) {
                try {
                    Log.d(TAG, "Waits until the render thread is ready to receive messages.");
                    mStartLock.wait();
                } catch (InterruptedException ie) { /* not expected */ }
            }
        }
    }

    private void shutdown() {
        Log.d(TAG, "shutdown");
        Looper.myLooper().quit();
    }


    // Prepares the surface.
    private void surfaceCreated() {
        Surface surface = mSurfaceHolder.getSurface();
        prepareGl(surface);
    }

    // Prepares window surface and GL state.
    private void prepareGl(Surface surface) {
        mWindowSurface = new WindowSurface(mEglCore, surface, false);
        mWindowSurface.makeCurrent();
        // Programs used for drawing onto the screen.
        mFlatProgram = new FlatShadedProgram();
        mTexProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D);

        mCoarseTexture = GeneratedTexture.createTestTexture(GeneratedTexture.Image.COARSE);
        mFineTexture = GeneratedTexture.createTestTexture(GeneratedTexture.Image.FINE);

        // Set the background color.
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        // Disable depth testing -- we're 2D only.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        // Don't need backface culling.  (If you're feeling pedantic, you can turn it on to
        // make sure we're defining our shapes correctly.)
        GLES20.glDisable(GLES20.GL_CULL_FACE);
    }


    /**
     * Handles changes to the size of the underlying surface.  Adjusts viewport as needed.
     * Must be called before we start drawing.
     * (Called from RenderHandler.)
     */
    private void surfaceChanged(int width, int height) {
        // This method is called when the surface is first created, and shortly after the
        // call to setFixedSize().  The tricky part is that this is called when the
        // drawing surface is *about* to change size, not when it has *already* changed
        // size.  A query on the EGL surface will confirm that the surface dimensions
        // haven't yet changed.  If you re-query after the next swapBuffers() call,
        // you will see the new dimensions.
        //
        // To have a smooth transition, we should continue to draw at the old size until the
        // surface query tells us that the size of the underlying buffers has actually
        // changed.  I don't really expect a "normal" app will want to call setFixedSize()
        // dynamically though, so in practice this situation shouldn't arise, and it's
        // just not worth the hassle of doing it right.

        // Use full window.
        GLES20.glViewport(0, 0, width, height);
        // Simple orthographic projection, with (0,0) in lower-left corner.
        Matrix.orthoM(mDisplayProjectionMatrix, 0, 0, width, 0, height, -1, 1);
        int smallDim = Math.min(width, height);
        // Set initial shape size / position / velocity based on window size.  Movement
        // has the same "feel" on all devices, but the actual path will vary depending
        // on the screen proportions.  We do it here, rather than defining fixed values
        // and tweaking the projection matrix, so that our squares are square.
        mTri.setColor(0.1f, 0.9f, 0.1f);
        mTri.setTexture(mFineTexture);
        mTri.setScale(smallDim / 3.0f, smallDim / 3.0f);
        mTri.setPosition(width / 2.0f, height / 2.0f);
        mRect.setColor(0.9f, 0.1f, 0.1f);
        mRect.setTexture(mCoarseTexture);
        mRect.setScale(smallDim / 5.0f, smallDim / 5.0f);
        mRect.setPosition(width / 2.0f, height / 2.0f);
        mRectVelX = 1 + smallDim / 4.0f;
        mRectVelY = 1 + smallDim / 5.0f;
        // left edge
        float edgeWidth = 1 + width / 64.0f;
        mEdges[0].setColor(0.5f, 0.5f, 0.5f);
        mEdges[0].setScale(edgeWidth, height);
        mEdges[0].setPosition(edgeWidth / 2.0f, height / 2.0f);
        // right edge
        mEdges[1].setColor(0.5f, 0.5f, 0.5f);
        mEdges[1].setScale(edgeWidth, height);
        mEdges[1].setPosition(width - edgeWidth / 2.0f, height / 2.0f);
        // top edge
        mEdges[2].setColor(0.5f, 0.5f, 0.5f);
        mEdges[2].setScale(width, edgeWidth);
        mEdges[2].setPosition(width / 2.0f, height - edgeWidth / 2.0f);
        // bottom edge
        mEdges[3].setColor(0.5f, 0.5f, 0.5f);
        mEdges[3].setScale(width, edgeWidth);
        mEdges[3].setPosition(width / 2.0f, edgeWidth / 2.0f);
        // Inner bounding rect, used to bounce objects off the walls.
        mInnerLeft = mInnerBottom = edgeWidth;
        mInnerRight = width - 1 - edgeWidth;
        mInnerTop = height - 1 - edgeWidth;
        Log.d(TAG, "mTri: " + mTri);
        Log.d(TAG, "mRect: " + mRect);
    }

    /**
     * Releases most of the GL resources we currently hold.
     * Does not release EglCore.
     */
    private void releaseGl() {
        GlUtil.checkGlError("releaseGl start");

        if (mWindowSurface != null) {
            mWindowSurface.release();
            mWindowSurface = null;
        }
        if (mFlatProgram != null) {
            mFlatProgram.release();
            mFlatProgram = null;
        }
        if (mTexProgram != null) {
            mTexProgram.release();
            mTexProgram = null;
        }
        GlUtil.checkGlError("releaseGl done");

        mEglCore.makeNothingCurrent();
    }





    // Handles the frame update.  Runs when Choreographer signals.
    private void doFrame(long timeStampNanos) {
        update(timeStampNanos);

        long diff = (System.nanoTime() - timeStampNanos) / 1000000;
        if (diff > 15) {
            // too much, drop a frame
            Log.d(TAG, "diff is " + diff + ", skipping render");
            return;
        }

        draw();
        mWindowSurface.swapBuffers();
    }

    // Previous frame time.
    private long mPrevTimeNanos;
    private void update(long timeStampNanos) {
        long intervalNanos;
        if (mPrevTimeNanos == 0) {
            intervalNanos = 0;
        } else {
            intervalNanos = timeStampNanos - mPrevTimeNanos;
            final long ONE_SECOND_NANOS = 1000000000L;
            if (intervalNanos > ONE_SECOND_NANOS) {
                Log.d(TAG, "Time delta too large: " +
                        (double) intervalNanos / ONE_SECOND_NANOS + " sec");
                intervalNanos = 0;
            }
        }
        mPrevTimeNanos = timeStampNanos;

        final float ONE_BILLION_F = 1000000000.0f;
        final float elapsedSeconds = intervalNanos / ONE_BILLION_F;
        // Spin the triangle.  We want one full 360-degree rotation every 3 seconds,
        // or 120 degrees per second.
        final int SECS_PER_SPIN = 3;
        float angleDelta = (360.0f / SECS_PER_SPIN) * elapsedSeconds;
        mTri.setRotation(mTri.getRotation() + angleDelta);
        // Bounce the rect around the screen.  The rect is a 1x1 square scaled up to NxN.
        // We don't do fancy collision detection, so it's possible for the box to slightly
        // overlap the edges.  We draw the edges last, so it's not noticeable.
        float xpos = mRect.getPositionX();
        float ypos = mRect.getPositionY();
        float xscale = mRect.getScaleX();
        float yscale = mRect.getScaleY();
        xpos += mRectVelX * elapsedSeconds;
        ypos += mRectVelY * elapsedSeconds;
        if ((mRectVelX < 0 && xpos - xscale/2 < mInnerLeft) ||
                (mRectVelX > 0 && xpos + xscale/2 > mInnerRight+1)) {
            mRectVelX = -mRectVelX;
        }
        if ((mRectVelY < 0 && ypos - yscale/2 < mInnerBottom) ||
                (mRectVelY > 0 && ypos + yscale/2 > mInnerTop+1)) {
            mRectVelY = -mRectVelY;
        }
        mRect.setPosition(xpos, ypos);
    }

    private void draw() {
        GlUtil.checkGlError("draw start");

        GLES20.glClearColor(0.2f, 0.2f, 0.2f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        // Textures may include alpha, so turn blending on.
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        if (mUseFlatShading) {
            mTri.draw(mFlatProgram, mDisplayProjectionMatrix);
            mRect.draw(mFlatProgram, mDisplayProjectionMatrix);
        } else {
            mTri.draw(mTexProgram, mDisplayProjectionMatrix);
            mRect.draw(mTexProgram, mDisplayProjectionMatrix);
        }
        GLES20.glDisable(GLES20.GL_BLEND);
        for (int i = 0; i < 4; i++) {
            mEdges[i].draw(mFlatProgram, mDisplayProjectionMatrix);
        }
        GlUtil.checkGlError("draw done");
    }






    /**
     * Handler for RenderThread.  Used for messages sent from the UI thread to the render thread.
     *
     * The object is created on the render thread, and the various "send" methods are called
     * from the UI thread.
     */
    public static class RenderHandler extends Handler {
        private static final int MSG_SURFACE_CREATED = 0;
        private static final int MSG_SURFACE_CHANGED = 1;
        private static final int MSG_DO_FRAME = 2;
        private static final int MSG_FLAT_SHADING = 3;
        private static final int MSG_SHUTDOWN = 5;
        private WeakReference<HSRenderThread> mWeakRenderThread;

        public RenderHandler(HSRenderThread rt) {
            mWeakRenderThread = new WeakReference<HSRenderThread>(rt);
        }

        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            HSRenderThread renderThread = mWeakRenderThread.get();
            if (renderThread == null) {
                Log.w(TAG, "RenderHandler.handleMessage: weak ref is null");
                return;
            }

            switch (what) {
                case MSG_SURFACE_CREATED:
                    renderThread.surfaceCreated();
                    break;
                case MSG_SURFACE_CHANGED:
                    renderThread.surfaceChanged(msg.arg1, msg.arg2);
                    break;
                case MSG_DO_FRAME:
                    long timestamp = (((long) msg.arg1) << 32) |
                            (((long) msg.arg2) & 0xffffffffL);
                    renderThread.doFrame(timestamp);
                    break;
                case MSG_FLAT_SHADING:
                    renderThread.setFlatShading(msg.arg1 != 0);
                    break;
                case MSG_SHUTDOWN:
                    renderThread.shutdown();
                    break;
                default:
                    throw new RuntimeException("unknown message " + what);
            }
        }

        public void sendSetFlatShading(boolean useFlatShading) {
            // ignore format
            sendMessage(obtainMessage(MSG_FLAT_SHADING, useFlatShading ? 1:0, 0));
        }
        public void sendSurfaceCreated() {
            sendMessage(obtainMessage(MSG_SURFACE_CREATED));
        }
        public void sendSurfaceChanged(@SuppressWarnings("unused") int format,
                                       int width, int height) {
            sendMessage(obtainMessage(MSG_SURFACE_CHANGED, width, height));
        }
        public void sendShutdown() {
            sendMessage(obtainMessage(RenderHandler.MSG_SHUTDOWN));
        }
        public void sendDoFrame(long frameTimeNanos) {
            sendMessage(obtainMessage(MSG_DO_FRAME,
                    (int) (frameTimeNanos >> 32), (int) frameTimeNanos));
        }
    }
}
