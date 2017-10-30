package org.learn.test.gltextureview;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.TextureView;

import org.learn.test.gltextureview.egl.DefaultEGLConfigChooser;
import org.learn.test.gltextureview.egl.EGLManager;
import org.learn.test.gltextureview.egl.GLESVersion;
import org.learn.test.gltextureview.egl.RenderingThreadType;
import org.learn.test.gltextureview.egl.SurfaceColorSpec;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;


/**
 * Created by zzr on 2017/10/9.
 */

public class GLTextureView extends TextureView implements TextureView.SurfaceTextureListener {
    private static final String TAG = GLTextureView.class.getSimpleName();

    protected final Object lock = new Object();
    GL11 gl11;

    protected EGLManager eglManager = null;
    public EGLManager getEGLManager() {
        return eglManager;
    }

    GLESVersion version = GLESVersion.OpenGLES20;

    int surfaceWidth = 0;                           //surface texture width

    int surfaceHeight = 0;                          //surface texture height

    boolean destroyed = false;                      //Surface Destroyed

    boolean sleep = false;                          //Thread

    boolean initialized = false;                    //check EGL Initialized

    protected Renderer renderer = null;             //gl renderer callback

    EGLConfigChooser eglConfigChooser = null;       //package ConfigChooser

    RenderingThreadType renderingThreadType = RenderingThreadType.BackgroundThread;

    Thread backgroundThread = null;


    public GLTextureView(Context context) {
        super(context);
        setSurfaceTextureListener(this);
    }



    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        synchronized (lock) {
            surfaceWidth = width;
            surfaceHeight = height;

            if (!isInitialized()) {
                eglManager = new EGLManager();

                if (eglConfigChooser == null) {
                    // make default spec
                    // RGBA8 hasDepth hasStencil
                    eglConfigChooser = new DefaultEGLConfigChooser();
                }

                eglManager.initialize(eglConfigChooser, version);

                if (version == GLESVersion.OpenGLES11) {
                    gl11 = eglManager.getGL11();
                }

                eglManager.resize(surfaceTexture);

                if (renderingThreadType != RenderingThreadType.BackgroundThread) {
                    // UIThread || request
                    eglManager.bind();
                    renderer.onSurfaceCreated(gl11, eglManager.getEglConfig());
                    renderer.onSurfaceChanged(gl11, width, height);
                    eglManager.unbind();
                }

            } else {
                eglManager.resize(surfaceTexture);

                if (renderingThreadType != RenderingThreadType.BackgroundThread) {
                    // UIThread || request
                    eglManager.bind();
                    renderer.onSurfaceChanged(gl11, width, height);
                    eglManager.unbind();
                }
            }

            initialized = true;
            if (renderingThreadType == RenderingThreadType.BackgroundThread) {
                // background
                backgroundThread = createRenderingThread();
                backgroundThread.start();
            }

        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        synchronized (lock) {
            surfaceWidth = width;
            surfaceHeight = height;

            eglManager.resize(surfaceTexture);

            if (renderingThreadType != RenderingThreadType.BackgroundThread) {
                // UIThread || request
                eglManager.bind();
                renderer.onSurfaceChanged(gl11, width, height);
                eglManager.unbind();
            }
        }
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        destroyed = true;
        try {
            synchronized (lock) {

                if (renderingThreadType != RenderingThreadType.BackgroundThread) {
                    // UIThread || request
                    eglManager.bind();
                    renderer.onSurfaceDestroyed(gl11);
                    eglManager.releaseThread();
                }
            }

            if (backgroundThread != null) {
                try {
                    Log.d(TAG, "wait rendering thread");
                    // wait background thread
                    backgroundThread.join();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } finally {
            eglManager.destroy();
        }
        // auto release
        return true;
    }


    //Activity#onPause() || Fragment#onPause()
    public void onPause() {
        sleep = true;
    }

    //Activity#onResume() || Fragment#onResume()
    public void onResume() {
        sleep = false;
    }

    //check EGL Initialized
    public boolean isInitialized() {
        return initialized;
    }

    public void setRenderer(Renderer renderer) {
        synchronized (lock) {
            if (isInitialized()) {
                throw new UnsupportedOperationException("GLTextureView Initialized");
            }
            this.renderer = renderer;
        }
    }

    public void setVersion(GLESVersion version) {
        synchronized (lock) {
            if (isInitialized()) {
                throw new UnsupportedOperationException("GLTextureView Initialized");
            }
            this.version = version;
        }
    }

    /**
     * EGL Config setup
     * @param color
     * @param hasDepth
     * @param hasStencil
     */
    public void setSurfaceSpec(SurfaceColorSpec color, boolean hasDepth, boolean hasStencil) {
        DefaultEGLConfigChooser chooser = new DefaultEGLConfigChooser();
        chooser.setColorSpec(color);
        chooser.setDepthEnable(hasDepth);
        chooser.setStencilEnable(hasStencil);
        setEGLConfigChooser(chooser);
    }
    /**
     * set Config Chooser
     * @param eglConfigChooser
     */
    public void setEGLConfigChooser(EGLConfigChooser eglConfigChooser) {
        synchronized (lock) {
            if (isInitialized()) {
                throw new UnsupportedOperationException("GLTextureView Initialized");
            }
            this.eglConfigChooser = eglConfigChooser;
        }
    }

    ///////////////// About Render Thread ///////////////////////////////////////////////////
    public void setRenderingThreadType(RenderingThreadType renderingThreadType) {
        synchronized (lock) {
            if (isInitialized()) {
                throw new UnsupportedOperationException("GLTextureView Initialized");
            }

            this.renderingThreadType = renderingThreadType;
        }
    }

    /**
     * start rendering
     * call GLTextureView.onRendering()
     */
    public void requestRender() {
        synchronized (lock) {
            if (!isInitialized()) {
                throw new UnsupportedOperationException("GLTextureView not initialized");
            }

            onRendering();
        }
    }

    protected void onRendering() {
        eglManager.bind();
        {
            renderer.onDrawFrame(gl11);
        }
        eglManager.swapBuffers();
        eglManager.unbind();
    }

    protected Thread createRenderingThread() {
        return new Thread(){
            int width = 0;
            int height = 0;

            @Override
            public void run() {
                // created
                eglManager.bind();
                {
                    renderer.onSurfaceCreated(gl11, eglManager.getEglConfig());
                }
                eglManager.unbind();

                while (!destroyed) {
                    int sleepTime = 1;
                    if (!sleep) {
                        synchronized (lock) {
                            eglManager.bind();

                            if (width != surfaceWidth || height != surfaceHeight) {
                                width = surfaceWidth;
                                height = surfaceHeight;
                                renderer.onSurfaceChanged(gl11, width, height);
                            }

                            renderer.onDrawFrame(gl11);

                            if (!destroyed) {
                                eglManager.swapBuffers();
                            }
                            eglManager.unbind();
                        }
                    } else {
                        sleepTime = 10;
                    }

                    try {
                        // sleep rendering thread
                        Thread.sleep(sleepTime);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // destroy
                synchronized (lock) {
                    eglManager.bind();
                    renderer.onSurfaceDestroyed(gl11);
                    eglManager.releaseThread();
                }
            }
        };
    }
    ///////////////// Interface  ///////////////////////////////////////////////////
    public interface EGLConfigChooser {

        /**
         * @param egl
         * @param display
         * @param version
         * @return
         */
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, GLESVersion version);
    }

    public interface Renderer {
        /**
         * created EGLSurface.
         */
        public void onSurfaceCreated(GL10 gl, EGLConfig config);

        /**
         * remake EGLSurface.
         */
        public void onSurfaceChanged(GL10 gl, int width, int height);

        /**
         * rendering.
         */
        public void onDrawFrame(GL10 gl);

        /**
         * destroyed
         * @param gl
         */
        public void onSurfaceDestroyed(GL10 gl);
    }
}
