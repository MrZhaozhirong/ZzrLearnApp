package org.learn.test.gltextureview.egl;

/**
 * Created by zzr on 2017/10/9.
 */

import javax.microedition.khronos.egl.EGL10;

/**
 * OpenGL ES Version
 */
public enum GLESVersion {
    /**
     * OpenGL ES 1.0
     */
    OpenGLES11 {
        @Override
        public int[] getContextAttributes() {
            return null;
        }
    },

    /**
     * OpenGL ES 2.0
     */
    OpenGLES20 {
        @Override
        public int[] getContextAttributes() {
            return new int[] {
                    0x3098 /* EGL_CONTEXT_CLIENT_VERSION */, 2, EGL10.EGL_NONE
            };
        }
    };

    public abstract int[] getContextAttributes();
}