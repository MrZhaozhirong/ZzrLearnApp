package org.learn.test.grafika.component;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import org.learn.test.grafika.GrafikaMainActivity;

import java.io.File;
import java.io.IOException;

/**
 * Created by zzr on 2017/11/16.
 */

public class CircularEncoder {
    private static final String TAG = GrafikaMainActivity.TAG;
    private static final boolean DEBUG = true;

    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding
    private static final int IFRAME_INTERVAL = 1;           // sync frame every second

    /**
     * Callback function definitions.  CircularEncoder caller must provide one.
     */
    public interface Callback {
        /**
         * Called some time after saveVideo(), when all data has been written to the
         * output file.
         * @param status Zero means success, nonzero indicates failure.
         */
        void fileSaveComplete(int status);

        /**
         * Called occasionally.
         * @param totalTimeMsec Total length, in milliseconds, of buffered video.
         */
        void bufferStatus(long totalTimeMsec);
    }


    private EncoderThread mEncoderThread;
    private Surface mInputSurface;
    private MediaCodec mEncoder;

    /**
     * Configures encoder, and prepares the input Surface.

     * @param width Width of encoded video, in pixels.  Should be a multiple of 16.
     * @param height Height of encoded video, in pixels.  Usually a multiple of 16 (1080 is ok).
     * @param bitRate Target bit rate, in bits.
     * @param frameRate Expected frame rate.
     * @param desiredSpanSec How many seconds of video we want to have in our buffer at any time.
     */
    public CircularEncoder(int width, int height, int bitRate, int frameRate,
                           int desiredSpanSec, Callback cb) throws IOException {
        // The goal is to size the buffer so that we can accumulate N seconds worth of video,
        // where N is passed in as "desiredSpanSec".  If the codec generates data at roughly
        // the requested bit rate, we can compute it(total) as time * bitRate / bitsPerByte.
        //
        // Sync frames will appear every (frameRate * IFRAME_INTERVAL) frames.  If the frame
        // rate is higher or lower than expected, various calculations may not work out right.
        //
        // Since we have to start muxing from a sync frame, we want to ensure that there's
        // room for at least one full GOP in the buffer, preferrably two.
        if (desiredSpanSec < IFRAME_INTERVAL * 2) {
            throw new RuntimeException("Requested time span is too short: " + desiredSpanSec +
                    " vs. " + (IFRAME_INTERVAL * 2));
        }
        CircularEncoderBuffer encBuffer = new CircularEncoderBuffer(bitRate, frameRate, desiredSpanSec);

        // Set some properties.  Failing to specify some of these can cause
        // the MediaCodec configure() call to throw an unhelpful exception.
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        if (DEBUG) Log.d(TAG, "format: " + format);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();

        // Start the encoder thread last.
        // That way we're sure it can see all of the state we've initialized.
        mEncoderThread = new EncoderThread(mEncoder, encBuffer, cb);
        mEncoderThread.start();
        mEncoderThread.waitUntilReady();
    }

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return mInputSurface;
    }

    /**
     * Initiates saving the currently-buffered frames to the specified output file.
     * The data will be written as a .mp4 file.  The call returns immediately.
     * When the file save completes, the callback will be notified.

     * The file generation is performed on the encoder thread, which means we won't be
     * draining the output buffers while this runs.  It would be wise to stop submitting
     * frames during this time.
     */
    public boolean saveVideo(File outputFile) {
        Handler handler = mEncoderThread.getHandler();
        if(handler!=null){
            handler.sendMessage(handler.obtainMessage(
                    EncoderThread.EncoderHandler.MSG_SAVE_VIDEO, outputFile));
            return true;
        } else {
            return false;
        }
    }

    /**
     * Notifies the encoder thread that a new frame will shortly be provided to the encoder.
     * <p>
     * There may or may not yet be data available from the encoder output.  The encoder
     * has a fair mount of latency due to processing, and it may want to accumulate a
     * few additional buffers before producing output.  We just need to drain it regularly
     * to avoid a situation where the producer gets wedged up because there's no room for
     * additional frames.
     * <p>
     * If the caller sends the frame and then notifies us, it could get wedged up.  If it
     * notifies us first and then sends the frame, we guarantee that the output buffers
     * were emptied, and it will be impossible for a single additional frame to block
     * indefinitely.
     */
    public void frameAvailableSoon() {
        Handler handler = mEncoderThread.getHandler();
        if(handler!=null){
            handler.sendMessage(handler.obtainMessage(
                    EncoderThread.EncoderHandler.MSG_FRAME_AVAILABLE_SOON));
        }
    }

    /**
     * Shuts down the encoder thread, and releases encoder resources.
     * Does not return until the encoder thread has stopped.
     */
    public void shutdown() {
        if (DEBUG) Log.d(TAG, "releasing encoder objects");
        Handler handler = mEncoderThread.getHandler();
        if(handler!=null){
            handler.sendMessage(handler.obtainMessage(EncoderThread.EncoderHandler.MSG_SHUTDOWN));
        }
        try {
            mEncoderThread.join();
        } catch (InterruptedException ie) {
            Log.w(TAG, "Encoder thread join() was interrupted", ie);
        }
        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
    }




 }
