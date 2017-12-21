package org.learn.test.grafika.component;

import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.learn.test.grafika.GrafikaMainActivity;
import org.learn.test.grafika.gles.EglCore;
import org.learn.test.grafika.gles.FullFrameRect;
import org.learn.test.grafika.gles.Texture2dProgram;
import org.learn.test.grafika.gles.WindowSurface;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;


/**
 * Created by zzr on 2017/12/15.
 */

public class CameraRecordEncoder implements Runnable {
    private static final String TAG = GrafikaMainActivity.TAG;
    /**
     * Object is immutable, which means we can safely pass it between threads without
     * explicit synchronization (and don't need to worry about it getting tweaked out from
     * under us).
     * ????  make frame rate and iframe interval configurable?  Maybe use builder pattern
     *       with reasonable defaults for those and bit rate.
     */
    public static class EncoderConfig {
        final File mOutputFile;
        final int mWidth;
        final int mHeight;
        final int mBitRate;
        final EGLContext mEglContext;

        public EncoderConfig(File outputFile, int width, int height, int bitRate,
                             EGLContext sharedEglContext) {
            mOutputFile = outputFile;
            mWidth = width;
            mHeight = height;
            mBitRate = bitRate;
            mEglContext = sharedEglContext;
        }

        @Override
        public String toString() {
            return "EncoderConfig: " + mWidth + "x" + mHeight + " @" + mBitRate +
                    " to '" + mOutputFile.toString() + "' ctxt=" + mEglContext;
        }
    }

    // ----- accessed by multiple threads -----
    private volatile EncoderHandler mHandler;
    private volatile float[] transform = new float[16];
    private final Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;

    public boolean isRecording() {
        synchronized (mReadyFence) {
            return mRunning;
        }
    }

    /**
     * Tells the video recorder to refresh its EGL surface.  (Call from non-encoder thread.)
     */
    public void updateSharedContext(EGLContext sharedContext) {
        mHandler.sendMessage(mHandler.obtainMessage(EncoderHandler.MSG_UPDATE_SHARED_CONTEXT, sharedContext));
    }


    /**
     * Tells the video recorder what texture name to use.  This is the external texture that
     * we're receiving camera previews in.  (Call from non-encoder thread.)
     * Note: do something less clumsy
     */
    public void setTextureId(int id) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(EncoderHandler.MSG_SET_TEXTURE_ID, id, 0, null));
    }

    /**
     * Tells the video recorder to start recording.  (Call from non-encoder thread.)
     * Creates a new thread, which will create an encoder using the provided configuration.
     * Returns after the recorder thread has started and is ready to accept Messages.
     * The encoder may not yet be fully configured.
     */
    public void startRecording(EncoderConfig encoderConfig) {
        Log.d(TAG, "CameraRecordEncoder: startRecording()");
        synchronized (mReadyFence) {
            if (mRunning) {
                Log.w(TAG, "Encoder thread already running");
                return;
            }
            mRunning = true;
            new Thread(this, "CameraRecordEncoder").start();
            while (!mReady) {
                try {
                    // waiting for Encoder thread really start
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(EncoderHandler.MSG_START_RECORDING, encoderConfig));
    }

    /**
     * Tells the video recorder to stop recording.  (Call from non-encoder thread.)
     * Returns immediately; the encoder/muxer may not yet be finished creating the movie.
     * ????: have the encoder thread invoke a callback on the UI thread just before it shuts down
     * so we can provide reasonable status UI (let the caller know that movie encoding has completed).
     */
    public void stopRecording() {
        mHandler.sendMessage(mHandler.obtainMessage(EncoderHandler.MSG_STOP_RECORDING));
        mHandler.sendMessage(mHandler.obtainMessage(EncoderHandler.MSG_QUIT));
        // We don't know when these will actually finish (or even start).  We don't want to
        // delay the UI thread though, so we return immediately.
    }

    /**
     * Tells the video recorder that a new frame is available.  (Call from non-encoder thread.)
     *
     * This function sends a message and returns immediately.  This isn't sufficient --
     * we don't want the caller to latch a new frame until we're done with this one --
     * but we can get away with it so long as the input frame rate is reasonable and the encoder
     * thread doesn't stall.
     *
     * Note: either block here until the texture has been rendered onto the encoder surface,
     * or have a separate "block if still busy" method that the caller can execute immediately
     * before it calls updateTexImage().  The latter is preferred because we don't want to
     * stall the caller while this thread does work.
     */
    public void frameAvailable(SurfaceTexture mSurfaceTexture) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        Matrix.setIdentityM(transform, 0);
        mSurfaceTexture.getTransformMatrix(transform);
        long timestamp = mSurfaceTexture.getTimestamp();
        if (timestamp == 0) {
            // Seeing this after device is toggled off/on with power button.  The
            // first frame back has a zero timestamp.
            // MPEG4Writer thinks this is cause to abort() in native code, so it's very
            // important that we just ignore the frame.
            Log.w(TAG, "HEY: got SurfaceTexture with timestamp of zero");
            return;
        }
        mHandler.sendMessage(mHandler.obtainMessage(EncoderHandler.MSG_FRAME_AVAILABLE,
                (int) (timestamp >> 32), (int) timestamp, transform));
    }

    @Override
    public void run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Looper.prepare();
        synchronized (mReadyFence) {
            mHandler = new EncoderHandler(this);
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();

        Log.d(TAG, "Encoder thread exiting");
        synchronized (mReadyFence) {
            mReady = mRunning = false;
            mHandler = null;
        }
    }



    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    class EncoderHandler extends Handler {
        static final String TAG = GrafikaMainActivity.TAG;
        static final int MSG_START_RECORDING = 0;
        static final int MSG_STOP_RECORDING = 1;
        static final int MSG_FRAME_AVAILABLE = 2;
        static final int MSG_SET_TEXTURE_ID = 3;
        static final int MSG_UPDATE_SHARED_CONTEXT = 4;
        static final int MSG_QUIT = 5;

        private WeakReference<CameraRecordEncoder> mWeakEncoder;

        public EncoderHandler(CameraRecordEncoder encoder) {
            mWeakEncoder = new WeakReference<CameraRecordEncoder>(encoder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            CameraRecordEncoder encoder = mWeakEncoder.get();
            if (encoder == null) {
                Log.w(TAG, "EncoderHandler.handleMessage: encoder is null");
                return;
            }

            int what = inputMessage.what;
            Object obj = inputMessage.obj;

            switch (what) {
                case MSG_START_RECORDING:
                    encoder.handleStartRecording((CameraRecordEncoder.EncoderConfig) obj);
                    break;
                case MSG_STOP_RECORDING:
                    encoder.handleStopRecording();
                    break;
                case MSG_FRAME_AVAILABLE:
                    long timestamp = (((long) inputMessage.arg1) << 32) |
                            (((long) inputMessage.arg2) & 0xffffffffL);
                    encoder.handleFrameAvailable((float[]) obj, timestamp);
                    break;
                case MSG_SET_TEXTURE_ID:
                    encoder.handleSetTexture(inputMessage.arg1);
                    break;
                case MSG_UPDATE_SHARED_CONTEXT:
                    encoder.handleUpdateSharedContext((EGLContext) inputMessage.obj);
                    break;
                case MSG_QUIT:
                    Looper.myLooper().quit();
                    break;
                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }


    // ----- accessed exclusively by encoder thread -----
    private int mFrameNum;
    private int mTextureId;
    private CameraRecordEncoderCore2 mRecordEncoder;
    private EglCore mEglCore;
    private WindowSurface mInputWindowSurface;
    private FullFrameRect mFullScreen;

    // handle Start Recording.
    private void handleStartRecording(EncoderConfig config) {
        Log.d(TAG, "handleStartRecording " + config);
        mFrameNum = 0;
        prepareEncoder(config.mEglContext, config.mWidth, config.mHeight, config.mBitRate,
                config.mOutputFile);
    }

    private void prepareEncoder(EGLContext sharedContext, int width, int height, int bitRate,
                                File outputFile) {
        try {
            mRecordEncoder = new CameraRecordEncoderCore2(width, height, bitRate, outputFile);
            mRecordEncoder.audioRecord(false);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mEglCore = new EglCore(sharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface = new WindowSurface(mEglCore, mRecordEncoder.getInputSurface(), true);
        mInputWindowSurface.makeCurrent();

        mFullScreen = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
    }

    // Handles a request to stop encoding.
    private void handleStopRecording() {
        Log.d(TAG, "handleStopRecording");
        mRecordEncoder.audioRecord(true);
        mRecordEncoder.drainAudioEncoder(true);
        mRecordEncoder.drainEncoder(true);
        releaseEncoder();
    }

    private void releaseEncoder() {
        mRecordEncoder.release();
        if (mInputWindowSurface != null) {
            mInputWindowSurface.release();
            mInputWindowSurface = null;
        }
        if (mFullScreen != null) {
            mFullScreen.release(false);
            mFullScreen = null;
        }
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
    }

    /**
     * Handles notification of an available frame.
     *
     * The texture is rendered onto the encoder's input surface, along with a moving
     * box (just because we can).
     *
     * @param transform The texture transform, from SurfaceTexture.
     * @param timestampNanos The frame's timestamp, from SurfaceTexture.
     */
    private void handleFrameAvailable(float[] transform, long timestampNanos) {
        // 把之前的数据推到合成器MediaMuxer当中
        mRecordEncoder.drainEncoder(false);
        mRecordEncoder.drainAudioEncoder(false);
        // 把之前设置用于preview的 SurfaceTexture对应的纹理id传进去
        // 然后draw到这个mFullScreen里面
        mFullScreen.drawFrame(mTextureId, transform);
        drawBox(mFrameNum++);

        // mInputWindowSurface是获取 编码器的输入Surface创建的，这里对应就是喂养数据到编码器当中
        mInputWindowSurface.setPresentationTime(timestampNanos);
        mInputWindowSurface.swapBuffers();
    }

    private void drawBox(int posn) {
        final int width = mInputWindowSurface.getWidth();
        int xpos = (posn * 4) % (width - 50);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(xpos, 0, 100, 50);
        GLES20.glClearColor(1.0f, 0.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    /**
     * Sets the texture name that SurfaceTexture will use when frames are received.
     */
    private void handleSetTexture(int id) {
        //Log.d(TAG, "handleSetTexture " + id);
        mTextureId = id;
    }


    /**
     * Tears down the EGL surface and context we've been using to feed the MediaCodec input
     * surface, and replaces it with a new one that shares with the new context.
     * <p>
     * This is useful if the old context we were sharing with went away (maybe a GLSurfaceView
     * that got torn down) and we need to hook up with the new one.
     */
    private void handleUpdateSharedContext(EGLContext newSharedContext) {
        Log.d(TAG, "handleUpdatedSharedContext " + newSharedContext);
        // Release the EGLSurface and EGLContext.
        mInputWindowSurface.releaseEglSurface();
        mFullScreen.release(false);
        mEglCore.release();

        // Create a new EGLContext and recreate the window surface.
        mEglCore = new EglCore(newSharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface.recreate(mEglCore);
        mInputWindowSurface.makeCurrent();

        // Create new programs and such for the new context.
        mFullScreen = new FullFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
    }
}



