package org.learn.test.grafika.component;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.learn.test.grafika.GrafikaMainActivity;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;


/**
 * Object that encapsulates the encoder thread.
 * <p>
 * We want to sleep until there's work to do.  We don't actually know when a new frame
 * arrives at the encoder, because the other thread is sending frames directly to the
 * input surface.  We will see data appear at the decoder output, so we can either use
 * an infinite timeout on dequeueOutputBuffer() or wait() on an object and require the
 * calling app wake us.  It's very useful to have all of the buffer management local to
 * this thread -- avoids synchronization -- so we want to do the file muxing in here.
 * So, it's best to sleep on an object and do something appropriate when awakened.
 * <p>
 * This class does not manage the MediaCodec encoder startup/shutdown.  The encoder
 * should be fully started before the thread is created, and not shut down until this
 * thread has been joined.
 */
public class EncoderThread extends Thread {

    private static final String TAG = GrafikaMainActivity.TAG+"-EncoderThread";
    private static final boolean DEBUG = true;
    private MediaCodec mEncoder;
    private MediaFormat mEncodedFormat;
    private MediaCodec.BufferInfo mBufferInfo;

    private EncoderHandler mHandler;
    private CircularEncoderBuffer mEncBuffer;
    private CircularEncoder.Callback mCallback;

    private int mFrameNum;
    private final Object mLock = new Object();
    private volatile boolean mReady = false;

    public EncoderThread(MediaCodec mediaCodec, CircularEncoderBuffer encBuffer,
                         CircularEncoder.Callback callback) {
        mEncoder = mediaCodec;
        mEncBuffer = encBuffer;
        mCallback = callback;

        mBufferInfo = new MediaCodec.BufferInfo();
    }

    // Prepares the Looper, Handler, and signals anybody watching that we're ready to go.
    @Override
    public void run() {
        Looper.prepare();
        mHandler = new EncoderHandler(this);    // must create on encoder thread
        Log.d(TAG, "encoder thread ready");
        synchronized (mLock) {
            mReady = true;
            mLock.notify();    // signal waitUntilReady()
        }
        Looper.loop();


        synchronized (mLock) {
            mReady = false;
            mHandler = null;
        }
        Log.d(TAG, "looper quit");
    }

    public void waitUntilReady() {
        synchronized (mLock) {
            while (!mReady) {
                try {
                    mLock.wait();
                } catch (InterruptedException ie) { /* not expected */ }
            }
        }
    }

    /**
     * Returns the Handler used to send messages to the encoder thread.
     */
    public EncoderHandler getHandler() {
        synchronized (mLock) {
            // Confirm ready state.
            if (!mReady) {
                Log.w(TAG, "not ready");
                return null;
            }
        }
        return mHandler;
    }

    /**
     * Saves the encoder output to a .mp4 file.
     * <p>
     * We'll drain the encoder to get any lingering data, but we're not going to shut
     * the encoder down or use other tricks to try to "flush" the encoder.  This may
     * mean we miss the last couple of submitted frames if they're still working their
     * way through.
     * <p>
     * We may want to reset the buffer after this -- if they hit "capture" again right
     * away they'll end up saving video with a gap where we paused to write the file.
     */
    private void saveVideo(File outputFile) {
        if (DEBUG) Log.d(TAG, "saveVideo " + outputFile);

        int index = mEncBuffer.getFirstIndex();
        if (index < 0) {
            Log.w(TAG, "Unable to get first index");
            mCallback.fileSaveComplete(1);
            return;
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        MediaMuxer muxer = null;
        int result = -1;

        try {
            muxer = new MediaMuxer(outputFile.getPath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoTrack = muxer.addTrack(mEncodedFormat);
            muxer.start();

            do {
                ByteBuffer buf = mEncBuffer.getChunk(index, info);
                if (DEBUG) {
                    Log.d(TAG, "SAVE " + index + " flags=0x" + Integer.toHexString(info.flags));
                }
                muxer.writeSampleData(videoTrack, buf, info);
                index = mEncBuffer.getNextIndex(index);
            } while (index >= 0);
            result = 0;
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG, "muxer failed", e);
            result = 2;
        } finally {
            if (muxer != null) {
                muxer.stop();
                muxer.release();
            }
        }
        if (DEBUG) {
            Log.d(TAG, "muxer stopped, result=" + result);
        }
        mCallback.fileSaveComplete(result);
    }


    /**
     * Drains the encoder output.
     * <p>
     * See notes for {@link CircularEncoder#frameAvailableSoon()}.
     */
    private void frameAvailableSoon() {
        if (DEBUG) Log.d(TAG, "EncoderThread frameAvailableSoon "+mFrameNum);
        drainEncoder();

        mFrameNum++;
        if ((mFrameNum % 10) == 0) {        // ??? should base off frame rate or clock?
            mCallback.bufferStatus(mEncBuffer.computeTimeSpanUsec());
        }
    }

    // Drains all pending output from the decoder, and adds it to the circular buffer.
    public void drainEncoder() {
        final int TIMEOUT_USEC = 0;     // no timeout -- check for buffers, bail if none
        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
        while (true) {
            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);

            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                break;
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Should happen before receiving buffers, and should only happen once.
                // The MediaFormat contains the csd-0 and csd-1 keys, which we'll need
                // for MediaMuxer.  It's unclear what else MediaMuxer might want, so
                // rather than extract the codec-specific data and reconstruct a new
                // MediaFormat later, we just grab it here and keep it around.
                mEncodedFormat = mEncoder.getOutputFormat();
                Log.d(TAG, "encoder output format changed: " + mEncodedFormat);
            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);
                // let's ignore it
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out when we got the
                    // INFO_OUTPUT_FORMAT_CHANGED status.  The MediaMuxer won't accept
                    // a single big blob -- it wants separate csd-0/csd-1 chunks --
                    // so simply saving this off won't work.
                    if (DEBUG) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                    mEncBuffer.add(encodedData, mBufferInfo.flags,
                            mBufferInfo.presentationTimeUs);

                    if (DEBUG) {
                        Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer, ts=" +
                                mBufferInfo.presentationTimeUs);
                    }

                    mEncoder.releaseOutputBuffer(encoderStatus, false);

                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                        break;      // out of while
                    }
                }
            }
        }
    }


    /**
     * Tells the Looper to quit.
     */
    void shutdown() {
        if (DEBUG) Log.d(TAG, "shutdown");
        Looper.myLooper().quit();
    }


    /**
     * Handler for EncoderThread.  Used for messages sent from the UI thread
     * (or whatever is driving the encoder) to the encoder thread.
     * <p>
     * The object is created on the encoder thread.
     */
    public static class EncoderHandler extends Handler {
        public static final int MSG_FRAME_AVAILABLE_SOON = 1;
        public static final int MSG_SAVE_VIDEO = 2;
        public static final int MSG_SHUTDOWN = 3;
        // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
        // but no real harm in it.
        private WeakReference<EncoderThread> mWeakEncoderThread;

        /**
         * Constructor.  Instantiate object from encoder thread.
         */
        public EncoderHandler(EncoderThread et) {
            mWeakEncoderThread = new WeakReference<EncoderThread>(et);
        }

        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            EncoderThread encoderThread = mWeakEncoderThread.get();
            if (encoderThread == null) {
                Log.w(TAG, "EncoderHandler.handleMessage: weak ref is null");
                return;
            }
            switch (what){
                case MSG_SAVE_VIDEO:
                    encoderThread.saveVideo((File) msg.obj);
                    break;
                case MSG_FRAME_AVAILABLE_SOON:
                    encoderThread.frameAvailableSoon();
                    break;
                case MSG_SHUTDOWN:
                    encoderThread.shutdown();
                    break;
            }
        }

    }
}
