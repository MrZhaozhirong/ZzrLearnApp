package org.learn.test.mediadump;

import android.content.Context;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.MediaController;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 * Created by zzr on 2017/9/21.
 */

public class VideoDumpView extends GLSurfaceView implements MediaController.MediaPlayerControl {

    private static final String TAG = "VideoDump";
    VideoDumpRenderer mRenderer;
    private MediaPlayer mMediaPlayer = null;
    private MediaController mMediaController;
    private boolean mMediaControllerAttached = false;
    private BufferedWriter mImageListWriter = null;

    public VideoDumpView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        // GLSurfaceView uses RGB_5_6_5 by default.
        if (VideoDumpConfig.SET_CHOOSER) {
            setEGLConfigChooser(8, 8, 8, 8, 8, 8);
        }
        mRenderer = new VideoDumpRenderer(context);
        setRenderer(mRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);


    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        mMediaPlayer = new MediaPlayer();
        //mMediaPlayer = MediaPlayer.create(context, Uri.parse(VideoDumpConfig.VIDEO_URI));
        try {
            File videoFile = new File(VideoDumpConfig.VIDEO_URI);
            FileDescriptor fd = new FileInputStream(videoFile).getFD();
            mMediaPlayer.setDataSource(fd);

            class RGBFilter implements FilenameFilter {
                public boolean accept(File dir, String name) {
                    return (name.endsWith(VideoDumpConfig.IMAGE_SUFFIX) ||
                            name.endsWith(VideoDumpConfig.IMAGES_LIST));
                }
            }
            File dump_dir = new File(VideoDumpConfig.ROOT_DIR);
            File[] dump_files = dump_dir.listFiles(new RGBFilter());
            if(dump_files!=null){
                for (File dump_file :dump_files) {
                    dump_file.delete();
                }
            }

            File image_list = new File(VideoDumpConfig.ROOT_DIR
                    + VideoDumpConfig.IMAGES_LIST);
            mImageListWriter = new BufferedWriter(new FileWriter(image_list));

        } catch (IOException e) {
            e.printStackTrace();
        }

        queueEvent(new Runnable() {
            @Override
            public void run() {
                mRenderer.setMediaPlayer(mMediaPlayer);
                mRenderer.setImageListWriter(mImageListWriter);
            }
        });
        super.onResume();
    }

    @Override
    public void onPause() {
        stopPlayback();
        super.onPause();
    }

    public void stopPlayback() {
        Log.d(TAG, "stopPlayback");

        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mImageListWriter != null) {
            try {
                mImageListWriter.flush();
                mImageListWriter.close();
            } catch (java.io.IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        } else {
            Log.d(TAG, "image list file was not written successfully.");
        }
    }

    public void setMediaController(MediaController controller) {
        if (mMediaController != null) {
            mMediaController.hide();
        }
        mMediaController = controller;
    }


    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            if (!mMediaControllerAttached) {
                mMediaController.setMediaPlayer(this);
                View anchorView = this.getParent() instanceof View ?
                        (View)this.getParent() : this;
                mMediaController.setAnchorView(anchorView);
                mMediaController.setEnabled(true);
                mMediaControllerAttached = true;
                Log.w(TAG, "mMediaControllerAttached");
            } else {
                mMediaController.show(10000);
            }
        }
    }

    private boolean isInPlaybackState() {
        return (mMediaPlayer != null && mMediaPlayer.isPlaying());
    }

    //private class MyMediaPlayerControl implements MediaController.MediaPlayerControl
    //{

        @Override
        public void start() {
            mMediaPlayer.start();
        }

        @Override
        public void pause() {
            mMediaPlayer.pause();
            try {
                mImageListWriter.flush();
            } catch (java.io.IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }

        @Override
        public int getDuration() {
            return mMediaPlayer.getDuration();
        }

        @Override
        public int getCurrentPosition() {
            if (isInPlaybackState()) {
                return mMediaPlayer.getCurrentPosition();
            }
            return 0;
        }

        @Override
        public void seekTo(int pos) {
            mMediaPlayer.seekTo(pos);
        }

        @Override
        public boolean isPlaying() {
            return isInPlaybackState();
        }

        @Override
        public int getBufferPercentage() {
            return 1;
        }

        @Override
        public boolean canPause() {
            return true;
        }

        @Override
        public boolean canSeekBackward() {
            return true;
        }

        @Override
        public boolean canSeekForward() {
            return true;
        }

        @Override
        public int getAudioSessionId() {
            return 0;
        }
    //}

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        attachMediaController();
        return true;
    }
}
