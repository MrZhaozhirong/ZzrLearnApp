package org.learn.test.mediadump;

/**
 * Created by zzr on 2017/9/21.
 */

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Properties;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * A renderer to read each video frame from a media player, draw it over a surface
 * texture, dump the on-screen pixels into a buffer, and writes the pixels into
 * a rgb file on sdcard.
 */
public class VideoDumpRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "VideoDump";

    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;

    private FloatBuffer mTriangleVertices;
    private final float[] mTriangleVerticesData = {
            // X, Y, Z, U, V
            -1.0f, -1.0f, 0, 0.f, 0.f,
            1.0f, -1.0f, 0, 1.f, 0.f,
            -1.0f,  1.0f, 0, 0.f, 1.f,
            1.0f,  1.0f, 0, 1.f, 1.f,
    };

    private float[] mMVPMatrix = new float[16];
    private float[] mSTMatrix = new float[16];

    private final String mVertexShader =
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uSTMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "  gl_Position = uMVPMatrix * aPosition;\n" +
            "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
            "}\n";

    private final String mFragmentShader =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}\n";
    private int mProgram;
    private int mTextureID;
    private int muMVPMatrixHandle;
    private int muSTMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;

    private SurfaceTexture mSurfaceTexture;
    private boolean updateSurface = false;

    /**
     * Fields that reads video source and dumps to file.
     */
    // The media player that loads and decodes the video.
    // Not owned by this class.
    private MediaPlayer mMediaPlayer;
    // The frame number from media player.
    private int mFrameNumber = 0;
    // The frame number that is drawing on screen.
    private int mDrawNumber = 0;
    // The width and height of dumping block.
    private int mWidth = 0;
    private int mHeight = 0;
    // The offset of the dumping block.
    private int mStartX = 0;
    private int mStartY = 0;
    // A file writer to write the filenames of images.
    private BufferedWriter mImageListWriter;
    // A buffer to hold the dumping pixels.
    private ByteBuffer mBuffer = null;




    public VideoDumpRenderer(Context context) {
        mTriangleVertices = ByteBuffer.allocateDirect(
                mTriangleVerticesData.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTriangleVertices.put(mTriangleVerticesData).position(0);

        Matrix.setIdentityM(mSTMatrix, 0);
    }

    public void setMediaPlayer(MediaPlayer player) {
        Log.d(TAG, "setMediaPlayer ");
        mMediaPlayer = player;
    }

    public void setImageListWriter(BufferedWriter imageListWriter) {
        mImageListWriter = imageListWriter;
    }

    ////////////////////////// implements /////////////////////////////////////////////
    @Override
    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated");

        mProgram = createProgram(mVertexShader, mFragmentShader);
        if (mProgram == 0) {
            return;
        }
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (maPositionHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        checkGlError("glGetAttribLocation aTextureCoord");
        if (maTextureHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }
        muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        checkGlError("glGetUniformLocation uMVPMatrix");
        if (muMVPMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uMVPMatrix");
        }
        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
        checkGlError("glGetUniformLocation uSTMatrix");
        if (muSTMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uSTMatrix");
        }

        // Create our texture. This has to be done each time the surface is created.
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        mTextureID = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
        checkGlError("glBindTexture mTextureID");
        // Can't do mipmapping with media_player source
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        // Clamp to edge is the only option
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        checkGlError("glTexParameteri mTextureID");

        /*
        * Create the SurfaceTexture that will feed this textureID,
        * and pass it to the MediaPlayer
        */
        mSurfaceTexture = new SurfaceTexture(mTextureID);
        mSurfaceTexture.setOnFrameAvailableListener(this);

        Surface surface = new Surface(mSurfaceTexture);
        mMediaPlayer.setSurface(surface);
        surface.release();

        try {
            mMediaPlayer.prepare();
            //mMediaPlayer.start();
        } catch (IOException t) {
            Log.e(TAG, "media player prepare failed");
        }

        synchronized(this) {
            updateSurface = false;
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(TAG, "Surface size: " + width + "x" + height);
        int video_width = mMediaPlayer.getVideoWidth();
        int video_height = mMediaPlayer.getVideoHeight();
        Log.d(TAG, "Video size: " + video_width + "x" + video_height);

        // TODO: adjust video_width and video_height with the surface size.
        GLES20.glViewport(0, 0, video_width, video_height);

        mWidth = Math.min(VideoDumpConfig.MAX_DUMP_WIDTH, video_width);
        mHeight = Math.min(VideoDumpConfig.MAX_DUMP_HEIGHT, video_height);
        mStartX = video_width / mWidth / 2 * mWidth;
        mStartY = video_height / mHeight / 2 * mHeight;
        Log.d(TAG, "Offset mStart: " + mStartX + "x" + mStartY);

        int image_size = mWidth * mHeight * VideoDumpConfig.BYTES_PER_PIXEL;
        mBuffer = ByteBuffer.allocate(image_size);

        int bpp[] = new int[3];
        GLES20.glGetIntegerv(GLES20.GL_RED_BITS, bpp, 0);
        GLES20.glGetIntegerv(GLES20.GL_GREEN_BITS, bpp, 1);
        GLES20.glGetIntegerv(GLES20.GL_BLUE_BITS, bpp, 2);
        Log.d(TAG, "rgb bits: " + bpp[0] + "-" + bpp[1] + "-" + bpp[2]);

        // Save the properties into a xml file
        // so the RgbPlayer can understand the output format.
        try {
            Properties prop = new Properties();
            prop.setProperty("width", Integer.toString(mWidth));
            prop.setProperty("height", Integer.toString(mHeight));
            prop.setProperty("startX", Integer.toString(mStartX));
            prop.setProperty("startY", Integer.toString(mStartY));
            prop.setProperty("bytesPerPixel", Integer.toString(VideoDumpConfig.BYTES_PER_PIXEL));
            prop.setProperty("frameRate", Integer.toString(VideoDumpConfig.FRAME_RATE));
            prop.storeToXML(new FileOutputStream(VideoDumpConfig.ROOT_DIR + VideoDumpConfig.PROPERTY_FILE), "");
        } catch (java.io.IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        boolean isNewFrame = false;
        int frameNumber = 0;

        synchronized(this) {
            if (updateSurface) {
                isNewFrame = true;
                frameNumber = mFrameNumber;
                mSurfaceTexture.updateTexImage();
                mSurfaceTexture.getTransformMatrix(mSTMatrix);
                updateSurface = false;
            }
        }

        // Initial clear.
        GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
        GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        // Load the program, which is the basics rules to draw the vertexes and textures.
        GLES20.glUseProgram(mProgram);
        checkGlError("glUseProgram");
        // Activate the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
        // Load the vertexes coordinates. Simple here since it only draw a rectangle
        // that fits the whole screen.
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maPosition");
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        checkGlError("glEnableVertexAttribArray maPositionHandle");
        // Load the texture coordinates, which is essentially a rectangle that fits
        // the whole video frame.
        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(maTextureHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        checkGlError("glEnableVertexAttribArray maTextureHandle");

        // Set up the GL matrices.
        Matrix.setIdentityM(mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);
        // Draw a rectangle and render the video frame as a texture on it.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays");


        if (isNewFrame) {  // avoid duplicates.
            Log.d(TAG, mDrawNumber + "/" + frameNumber + " before dumping "
                    + System.currentTimeMillis());
            DumpToFile(frameNumber);
            Log.d(TAG, mDrawNumber + "/" + frameNumber + " after  dumping "
                    + System.currentTimeMillis());
            mDrawNumber++;
        }
        GLES20.glFinish();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        /* For simplicity, SurfaceTexture calls here when it has new
         * data available.  Call may come in from some random thread,
         * so let's be safe and use synchronize. No OpenGL calls can be done here.
         */
        Log.d(TAG, "onFrameAvailable | "+mFrameNumber);
        mFrameNumber++;
        updateSurface = true;
    }
    //////////////////////////end implements /////////////////////////////////////////////


    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }

        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    // Call the GL function that dumps the screen into a buffer, then write to a file.
    private void DumpToFile(int frameNumber) {
        GLES20.glReadPixels(mStartX, mStartY, mWidth, mHeight,
                VideoDumpConfig.PIXEL_FORMAT,
                VideoDumpConfig.PIXEL_TYPE,
                mBuffer);
        checkGlError("glReadPixels");

        Log.d(TAG, mDrawNumber + "/" + frameNumber + " after  glReadPixels " + System.currentTimeMillis());

        String filename =  VideoDumpConfig.ROOT_DIR + VideoDumpConfig.IMAGE_PREFIX
                + frameNumber + VideoDumpConfig.IMAGE_SUFFIX;
        try {
            mImageListWriter.write(filename);
            mImageListWriter.newLine();
            FileOutputStream fos = new FileOutputStream(filename);
            fos.write(mBuffer.array());
            fos.close();
        } catch (java.io.IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }




    private void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error );
            throw new RuntimeException(op + ": glError " + error);
        }
    }

}
