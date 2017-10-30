package org.learn.test.ndkgl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;

import org.learn.test.R;

import java.lang.ref.WeakReference;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by zzr on 2017/10/30.
 */

public class NDKGLView extends GLSurfaceView{
    private static String TAG = "NDKGLView";
    protected static final boolean DEBUG = false;

    public NDKGLView(Context context) {
        super(context);
        init(false, 0, 0);
        /* Set the renderer responsible for frame rendering */
        setRenderer(new Renderer(context));
    }

    public NDKGLView(Context context, boolean translucent, int depth, int stencil) {
        super(context);
        init(translucent, depth, stencil);
        /* Set the renderer responsible for frame rendering */
        setRenderer(new Renderer(context));
    }

    private void init(boolean translucent, int depth, int stencil) {
        /* By default, GLSurfaceView() creates a RGB_565 opaque surface.
         * If we want a translucent one, we should change the surface's
         * format here, using PixelFormat.TRANSLUCENT for GL Surfaces
         * is interpreted as any 32-bit surface with alpha by SurfaceFlinger.
         */
        if (translucent) {
            this.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        }
        /* Setup the context factory for 2.0 rendering.
         * See ContextFactory class definition below
         */
        setEGLContextFactory(new ContextFactory());
        setEGLContextClientVersion(2);
        /* We need to choose an EGLConfig that matches the format of
         * our surface exactly. This is going to be done in our
         * custom config chooser. See ConfigChooser class definition
         * below.
         */
        setEGLConfigChooser( translucent ?
                new GL2ConfigChooser(8, 8, 8, 8, depth, stencil) :
                new GL2ConfigChooser(5, 6, 5, 0, depth, stencil) );
    }

    private static void checkEglError(String prompt, EGL10 egl) {
        int error;
        while ((error = egl.eglGetError()) != EGL10.EGL_SUCCESS) {
            Log.e(TAG, String.format("%s: EGL error: 0x%x", prompt, error));
        }
    }

    private static class ContextFactory implements GLSurfaceView.EGLContextFactory {
        private static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
            Log.w(TAG, "creating OpenGL ES 2.0 context");
            checkEglError("Before eglCreateContext", egl);
            int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
            EGLContext context = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
            checkEglError("After eglCreateContext", egl);
            return context;
        }

        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            egl.eglDestroyContext(display, context);
        }
    }






    private static class Renderer implements GLSurfaceView.Renderer {
        private WeakReference<Context> ctx_wrf;
        public Renderer(Context context) {
            ctx_wrf = new WeakReference<>(context);
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig eglConfig) {
            if(ctx_wrf.get()==null)
                return;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            Bitmap bitmap = BitmapFactory.decodeResource(ctx_wrf.get().getResources(), R.mipmap.ic_launcher, options);
            if(bitmap != null)
            {
                final int[] textureObjectIds = new int[1];
                gl.glEnable(GLES20.GL_TEXTURE_2D);
                gl.glGenTextures(1, textureObjectIds,0);
                gl.glBindTexture(GLES20.GL_TEXTURE_2D, textureObjectIds[0]);
                gl.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                gl.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                gl.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                        GLES20.GL_CLAMP_TO_EDGE);
                gl.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                        GLES20.GL_CLAMP_TO_EDGE);
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

                libJNI2GL.setTextures(textureObjectIds);
                bitmap.recycle();
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl10, int width, int height) {
            libJNI2GL.init(width, height);
        }

        @Override
        public void onDrawFrame(GL10 gl10) {
            libJNI2GL.drawOnNDK();
        }
    }
}
