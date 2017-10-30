package org.learn.test.mediadump;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.MediaController;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by zzr on 2017/9/22.
 */

public class RgbPlayerActivity extends Activity {

    private static final String TAG = "RgbView";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(new RgbView(this));
    }


    private static class RgbView extends View implements MediaController.MediaPlayerControl {

        private int mStartX = 0;
        private int mStartY = 0;
        private int mWidth = 0;
        private int mHeight = 0;
        private int mFrameRate = 0;
        private int mBytesPerPixel = 0;
        private int mBytesPerLine = 0;
        private int mBytesPerImage = 0;
        private ByteBuffer mFlipBuf;
        private byte[] mImageBytes;
        private Bitmap mBitmap;

        private List<String> mImageList;
        private int mImageIndex = 0;
        private boolean mIsPlaying = false;

        private MediaController mMediaController;
        private boolean mMediaControllerAttached;
        private Timer mTimer;
        private TimerTask mImageTask = new TimerTask() {
            @Override
            public void run() {
                if (mIsPlaying) {
                    mImageIndex++;
                    LoadImage();
                    Log.w(TAG, "mImageTask run : "+mImageIndex);
                }
            }
        };
        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                invalidate();
            }
        };

        public RgbView(Context context) {
            super(context);
            // read properties
            Properties prop = new Properties();
            try {
                prop.loadFromXML(new FileInputStream(VideoDumpConfig.ROOT_DIR + VideoDumpConfig.PROPERTY_FILE));
            } catch (java.io.IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }

            try {
                mStartX = Integer.parseInt(prop.getProperty("startX"));
                mStartY = Integer.parseInt(prop.getProperty("startY"));
                mWidth = Integer.parseInt(prop.getProperty("width"));
                mHeight = Integer.parseInt(prop.getProperty("height"));
                mBytesPerPixel = Integer.parseInt(prop.getProperty("bytesPerPixel"));
                mFrameRate = Integer.parseInt(prop.getProperty("frameRate"));
            } catch (java.lang.NumberFormatException e) {
                Log.e(TAG, e.getMessage(), e);
            }

            mBytesPerLine = mWidth * mBytesPerPixel;
            mBytesPerImage = mHeight * mBytesPerLine;
            mFlipBuf = ByteBuffer.allocate(mBytesPerImage);
            mBitmap = Bitmap.createBitmap(mWidth, mHeight,
                    mBytesPerPixel == 2 ? Bitmap.Config.RGB_565 : Bitmap.Config.ARGB_8888);

            mImageList = new ArrayList<String>();
            try {
                BufferedReader reader = new BufferedReader(
                        new FileReader(VideoDumpConfig.ROOT_DIR + VideoDumpConfig.IMAGES_LIST));
                String line;
                while ((line = reader.readLine()) != null) {
                    mImageList.add(line);
                }
                reader.close();
            } catch (java.io.IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }

            mMediaController = new MediaController(context);
            mTimer = new Timer();
            LoadImage();
        }



        private void LoadImage() {
            try {
                if (mImageIndex < 0 || mImageIndex >= mImageList.size()) {
                    mImageIndex = 0;
                    mIsPlaying = false;
                }

                String filename = mImageList.get(mImageIndex);

                FileInputStream in = new FileInputStream(filename);
                mImageBytes = new byte[mBytesPerImage];
                in.read(mImageBytes);
            } catch (Exception e) {
                Log.e("Error reading file", e.toString());
            }
            // Flip the image vertically since the image from MediaDump is upside down.
            for (int i = mHeight - 1; i >= 0; i--) {
                mFlipBuf.put(mImageBytes, i * mBytesPerLine, mBytesPerLine);
            }
            mFlipBuf.rewind();
            mBitmap.copyPixelsFromBuffer(mFlipBuf);
            mFlipBuf.rewind();
            mHandler.sendEmptyMessage(0);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawBitmap(mBitmap, mStartX, mStartY, null);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            attachMediaController();
            return true;
        }

        private void attachMediaController() {
            if (mMediaController != null) {
                if (!mMediaControllerAttached) {
                    mMediaController.setMediaPlayer(this);
                    View anchorView = this.getParent() instanceof View ?
                            (View)this.getParent() : this;
                    mMediaController.setAnchorView(anchorView);
                    mMediaController.setEnabled(true);
                    mMediaControllerAttached = true;
                    //mIsPlaying = true;
                    mTimer.scheduleAtFixedRate(mImageTask, 0, 1000 / mFrameRate);
                } else {
                    mMediaController.show(5000);
                }
            }
        }

        @Override
        public void start() {
            Log.w(TAG, "start ! ");
            mIsPlaying = true;
        }

        @Override
        public void pause() {
            Log.w(TAG, "pause ! ");
            mIsPlaying = false;
        }

        @Override
        public int getDuration() {
            Log.w(TAG, "getDuration ! "+mImageList.size() * 1000 / mFrameRate);
            return mImageList.size() * 1000 / mFrameRate;
        }

        @Override
        public int getCurrentPosition() {
            Log.w(TAG, "getCurrentPosition ! "+mImageIndex * 1000 / mFrameRate);
            return mImageIndex * 1000 / mFrameRate;
        }

        @Override
        public void seekTo(int pos) {
            mImageIndex = pos * mFrameRate / 1000;
            Log.w(TAG, "seekTo ! "+mImageIndex);
        }

        @Override
        public boolean isPlaying() {
            return mIsPlaying;
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
    } //End RgbView




}
