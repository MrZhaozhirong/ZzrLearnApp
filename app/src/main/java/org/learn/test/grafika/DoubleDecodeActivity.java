package org.learn.test.grafika;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import org.learn.test.R;
import org.learn.test.grafika.component.MoviePlayer;
import org.learn.test.grafika.component.SpeedControlCallback;
import org.learn.test.grafika.util.ContentManager;

import java.io.File;
import java.io.IOException;

/**
 * Created by zzr on 2017/11/22.
 */

public class DoubleDecodeActivity extends Activity {
    private static final String TAG = GrafikaMainActivity.TAG;
    private static final int VIDEO_COUNT = 2;
    // Must be static storage so they'll survive Activity restart.
    private static boolean sVideoRunning = false;

    private static VideoBlob[] sBlob = new VideoBlob[VIDEO_COUNT];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.grafika_double_decode);

        Log.w(TAG, "onCreate sVideoRunning:"+sVideoRunning);
        if (!sVideoRunning) {
            sBlob[0] = new VideoBlob((TextureView) findViewById(R.id.double1_texture_view),
                    ContentManager.MOVIE_SLIDERS, 0);
            sBlob[1] = new VideoBlob((TextureView) findViewById(R.id.double2_texture_view),
                    ContentManager.MOVIE_EIGHT_RECTS, 1);
            sVideoRunning = true;
        } else {
            sBlob[0].recreateView((TextureView) findViewById(R.id.double1_texture_view));
            sBlob[1].recreateView((TextureView) findViewById(R.id.double2_texture_view));
        }
    }


    @Override
    protected void onPause() {
        super.onPause();

        boolean finishing = isFinishing();
        Log.d(TAG, "isFinishing: " + finishing);
        for (int i = 0; i < VIDEO_COUNT; i++) {
            sBlob[i].stopPlayback();
            //sBlob[i] = null;
        }
        sVideoRunning = !finishing;
        Log.d(TAG, "onPause complete");
    }



    /**
     * Video playback blob.
     * <p>
     * Encapsulates the video decoder and playback surface.
     * <p>
     * We want to avoid tearing down and recreating the video decoder on orientation changes,
     * because it can be expensive to do so.  That means keeping the decoder's output Surface
     * around, which means keeping the SurfaceTexture around.
     * <p>
     * It's possible that the orientation change will cause the UI thread's EGL context to be
     * torn down and recreated (the app framework docs don't seem to make any guarantees here),
     * so we need to detach the SurfaceTexture from EGL on destroy, and reattach it when
     * the new SurfaceTexture becomes available.  Happily, TextureView does this for us.
     */
    private static class VideoBlob implements TextureView.SurfaceTextureListener {
        private final String TAG_VideoBlob;
        private int mMovieTag;
        private TextureView mTextureView;
        private SpeedControlCallback mCallback;
        private PlayMovieThread mPlayThread;
        private SurfaceTexture mSavedSurfaceTexture;

        /**
         * Constructs the VideoBlob.
         * @param view The TextureView object we want to draw into.
         * @param movieTag Which movie to play.
         * @param ordinal The blob's ordinal (only used for log messages).
         */
        public VideoBlob(TextureView view, int movieTag, int ordinal) {
            TAG_VideoBlob = TAG + ordinal;
            Log.d(TAG_VideoBlob, "VideoBlob: tag=" + movieTag + " view=" + view);
            mMovieTag = movieTag;

            mCallback = new SpeedControlCallback();

            recreateView(view);
        }

        /**
         * Performs partial construction.
         * The VideoBlob is already created, but the Activity was recreated,
         * so we need to update our view.
         */
        public void recreateView(TextureView view) {
            Log.d(TAG_VideoBlob, "recreateView: " + view);
            mTextureView = view;
            if (mSavedSurfaceTexture != null) {
                Log.d(TAG_VideoBlob, "use saved st=" + mSavedSurfaceTexture);
                mTextureView.setSurfaceTexture(mSavedSurfaceTexture);
            }
            mTextureView.setSurfaceTextureListener(this);
        }

        /**
         * Stop playback and shut everything down.
         */
        public void stopPlayback() {
            Log.d(TAG_VideoBlob, "stopPlayback");
            mPlayThread.requestStop();
            mPlayThread.waitForStop();
            // We don't need this any more, so null it out.  This also serves as a signal
            // to let onSurfaceTextureDestroyed() know that it can tell TextureView to
            // free the SurfaceTexture.
            mSavedSurfaceTexture = null;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            if (mSavedSurfaceTexture == null) {
                mSavedSurfaceTexture = surface;
                File sliders = ContentManager.getInstance().getPath(mMovieTag);
                mPlayThread = new PlayMovieThread(sliders, new Surface(surface), mCallback);
            } else {
                // Can't do it here in Android <= 4.4.  The TextureView doesn't add a
                // listener on the new SurfaceTexture, so it never sees any updates.
                // Needs to happen from activity onCreate() -- see recreateView().
                //Log.d(LTAG, "using saved st=" + mSavedSurfaceTexture);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            Log.d(TAG_VideoBlob, "onSurfaceTextureSizeChanged size=" + width + "x" + height + ", st=" + surface);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            Log.d(TAG_VideoBlob, "onSurfaceTextureDestroyed st=" + surface);
            // The SurfaceTexture is already detached from the EGL context at this point,
            // so we don't need to do that.
            //
            // The saved SurfaceTexture will be null if we're shutting down, so we want to
            // return "true" in that case (indicating that TextureView can release the ST).
            return (mSavedSurfaceTexture == null);
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            //Log.d(TAG, "onSurfaceTextureUpdated st=" + surface);
        }
    }



    /**
     * Thread object that plays a movie from a file to a surface.
     *
     * Currently loops until told to stop.
     */
    private static class PlayMovieThread extends Thread {
        private final File mFile;
        private final Surface mSurface;
        private final SpeedControlCallback mCallback;
        private MoviePlayer mMoviePlayer;
        private final Object mStopLock = new Object();
        private boolean mStopped = false;
        /**
         * Creates thread and starts execution.
         * <p>
         * The object takes ownership of the Surface, and will access it from the new thread.
         * When playback completes, the Surface will be released.
         */
        public PlayMovieThread(File file, Surface surface, SpeedControlCallback callback) {
            mFile = file;
            mSurface = surface;
            mCallback = callback;
            start();
        }

        //Asks MoviePlayer to halt playback.  Returns without waiting for playback to halt.
        public void requestStop() {
            mMoviePlayer.requestStop();
        }
        public void waitForStop() {
            synchronized (mStopLock) {
                while (!mStopped) {
                    try {
                        mStopLock.wait();
                    } catch (InterruptedException ie) {
                        // discard
                    }
                }
            }
        }

        @Override
        public void run() {
            try {
                mMoviePlayer = new MoviePlayer(mFile, mSurface, mCallback);
                mMoviePlayer.setLoopMode(true);
                mMoviePlayer.play();
            } catch (IOException ioe) {
                Log.e(TAG, "movie playback failed", ioe);
            } finally {
                mSurface.release();
                synchronized (mStopLock) {
                    mStopped = true;
                    mStopLock.notifyAll();
                }
                Log.d(TAG, "PlayMovieThread stopping");
            }
        }
    }
}
