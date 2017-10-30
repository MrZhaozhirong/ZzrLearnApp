package org.learn.test.mediadump;

import android.opengl.GLES20;

/**
 * Created by zzr on 2017/9/21.
 */

public class VideoDumpConfig {

    public static final String VIDEO_URI = "/sdcard/mediadump/sample.mp4";
    public static final String ROOT_DIR = "/sdcard/mediadump/";
    public static final String IMAGES_LIST = "images.lst";
    public static final String PROPERTY_FILE = "prop.xml";
    public static final String IMAGE_PREFIX = "img";
    public static final String IMAGE_SUFFIX = ".rgb";

    // So far, glReadPixels only supports two (format, type) combinations
    //     GL_RGB  GL_UNSIGNED_SHORT_5_6_5   16 bits per pixel (default)
    //     GL_RGBA GL_UNSIGNED_BYTE          32 bits per pixel
    public static final int PIXEL_FORMAT = GLES20.GL_RGBA;
    public static final int PIXEL_TYPE = PIXEL_FORMAT == GLES20.GL_RGBA
                                        ? GLES20.GL_UNSIGNED_BYTE : GLES20.GL_UNSIGNED_SHORT_5_6_5;

    public static final boolean SET_CHOOSER = PIXEL_FORMAT == GLES20.GL_RGBA ? true : false;

    public static final int BYTES_PER_PIXEL = PIXEL_FORMAT == GLES20.GL_RGBA ? 4 : 2;

    // On Motorola Xoom, it takes 100ms to read pixels and 180ms to write to a file
    // to dump a complete 720p(1280*720) video frame. It's much slower than the frame
    // playback interval (40ms).
    // So we only dump a center block and it should be able to catch all the e2e distortion.
    // A reasonable size of the block is 256x256, which takes 4ms to read pixels and 25 ms to write to a file.
    public static final int MAX_DUMP_WIDTH = 256;
    public static final int MAX_DUMP_HEIGHT = 256;

    // TODO: MediaPlayer doesn't give back the video frame rate and we'll need to
    // figure it by dividing the total number of frames by the duration.
    public static final int FRAME_RATE = 25;
}
