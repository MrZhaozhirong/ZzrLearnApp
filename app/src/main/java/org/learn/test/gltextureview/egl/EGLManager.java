package org.learn.test.gltextureview.egl;


import android.graphics.SurfaceTexture;
import android.os.Looper;

import org.learn.test.gltextureview.GLTextureView;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL11;

/**
 * Created by zzr on 2017/10/9.
 */

public class EGLManager {
    final private Object lock = new Object();

    EGL10 egl = null;

    EGLDisplay systemDisplay = null;

    EGLSurface systemReadSurface = null;

    EGLSurface systemDrawSurface = null;

    EGLContext systemContext = null;

    /**
     * GL10 object
     * only OpenGL ES 1.1
     */
    GL11 gl11 = null;

    public GL11 getGL11() {
        if (gl11 == null) {
            throw new UnsupportedOperationException("OpenGL ES 1.1 only");
        }
        return gl11;
    }

    private EGLDisplay eglDisplay = null;

    private EGLConfig eglConfig = null;
    public EGLConfig getEglConfig() {
        return eglConfig;
    }

    private EGLContext eglContext = null;
    public EGLContext getEglContext() {
        return eglContext;
    }

    private EGLSurface eglSurface = null;
    public EGLSurface getEglSurface() {
        return eglSurface;
    }

    public EGLManager() { }




    public void initialize(final GLTextureView.EGLConfigChooser chooser,
                           final GLESVersion version){
        synchronized (lock) {
            if (egl != null) {
                throw new RuntimeException("initialized");
            }

            egl = (EGL10) EGLContext.getEGL();
            {
                systemDisplay = egl.eglGetCurrentDisplay();
                systemReadSurface = egl.eglGetCurrentSurface(EGL10.EGL_READ);
                systemDrawSurface = egl.eglGetCurrentSurface(EGL10.EGL_DRAW);
                systemContext = egl.eglGetCurrentContext();
            }

            {
                eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
                if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                    throw new RuntimeException("EGL_NO_DISPLAY");
                }

                if (!egl.eglInitialize(eglDisplay, new int[2])) {
                    throw new RuntimeException("eglInitialize");
                }

                eglConfig = chooser.chooseConfig(egl, eglDisplay, version);
                if (eglConfig == null) {
                    throw new RuntimeException("chooseConfig");
                }

                eglContext = egl.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT,
                        version.getContextAttributes());

                if (eglContext == EGL10.EGL_NO_CONTEXT) {
                    throw new RuntimeException("eglCreateContext");
                }
            }

            if (version == GLESVersion.OpenGLES11) {
                gl11 = (GL11) eglContext.getGL();
            }
        }
    }


    public void resize(SurfaceTexture surface){
        synchronized (lock){
            if(eglSurface!=null){
                egl.eglDestroySurface(eglDisplay, eglSurface);
            }
            //重新创建
            {
                eglSurface=egl.eglCreateWindowSurface(eglDisplay, eglConfig, surface, null);
                if (eglSurface == EGL10.EGL_NO_SURFACE) {
                    throw new RuntimeException("eglCreateWindowSurface");
                }
            }
        }
    }


    public void destroy(){
        synchronized (lock){
            if(egl == null){
                return;
            }
            if(eglSurface!=null){
                egl.eglDestroySurface(eglDisplay, eglSurface);
            }
            if(eglContext!=null){
                egl.eglDestroyContext(eglDisplay, eglContext);
            }
            eglConfig = null;
            egl = null;
        }
    }


    public void bind() {
        synchronized (lock) {
            egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
        }
    }

    public boolean isUIThread() {
        return Thread.currentThread().equals(Looper.getMainLooper().getThread());
    }

    public void unbind() {
        synchronized (lock) {
            if (isUIThread()) {
                // UIスレッドならばシステムのデフォルトへ返す
                egl.eglMakeCurrent(systemDisplay, systemDrawSurface, systemReadSurface, systemContext);
            } else {
                // それ以外ならば、null状態に戻す
                egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            }
        }
    }

    public void releaseThread() {
        synchronized (lock) {
            egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        }
    }

    public boolean swapBuffers() {
        synchronized (lock) {
            return egl.eglSwapBuffers(eglDisplay, eglSurface);
        }
    }
}
