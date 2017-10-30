package org.learn.test.gltextureview.egl;

import org.learn.test.gltextureview.GLTextureView;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Created by zzr on 2017/10/11.
 */

public class DefaultEGLConfigChooser implements GLTextureView.EGLConfigChooser {

    SurfaceColorSpec mColorSpec = SurfaceColorSpec.RGBA8888;

    boolean mDepthEnable = true;

    boolean mStencilEnable = false;

    public DefaultEGLConfigChooser() { }

    public void setColorSpec(SurfaceColorSpec colorSpec) {
        this.mColorSpec = colorSpec;
    }

    public void setDepthEnable(boolean depthEnable) {
        this.mDepthEnable = depthEnable;
    }

    public void setStencilEnable(boolean stencilEnable) {
        this.mStencilEnable = stencilEnable;
    }


    @Override
    public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display, GLESVersion version) {

        EGLConfig[] configs = new EGLConfig[32];

        //获取系统当前支持多少中configs
        int[] config_num = new int[1];
        if (!egl.eglChooseConfig(display, getConfigSpec(version), configs, configs.length, config_num)) {
            throw new RuntimeException("eglChooseConfig");
        }
        final int CONFIG_NUM = config_num[0];

        final int r_bits = mColorSpec.getRedSize();
        final int g_bits = mColorSpec.getGreenSize();
        final int b_bits = mColorSpec.getBlueSize();
        final int a_bits = mColorSpec.getAlphaSize();
        final int d_bits = mDepthEnable ? 16 : 0;
        final int s_bits = mStencilEnable ? 8 : 0;

        for (int i = 0; i < CONFIG_NUM; ++i) {//与系统每一种匹对，寻找适合的返回。
            final EGLConfig checkConfig = configs[i];

            final int default_config_r = getConfigAttrib(egl, display, checkConfig, EGL10.EGL_RED_SIZE);
            final int default_config_g = getConfigAttrib(egl, display, checkConfig, EGL10.EGL_GREEN_SIZE);
            final int default_config_b = getConfigAttrib(egl, display, checkConfig, EGL10.EGL_BLUE_SIZE);
            final int default_config_a = getConfigAttrib(egl, display, checkConfig, EGL10.EGL_ALPHA_SIZE);
            final int default_config_d = getConfigAttrib(egl, display, checkConfig, EGL10.EGL_DEPTH_SIZE);
            final int default_config_s = getConfigAttrib(egl, display, checkConfig, EGL10.EGL_STENCIL_SIZE);

            if (default_config_r == r_bits && default_config_g == g_bits && default_config_b == b_bits
                    && default_config_a >= a_bits && default_config_d >= d_bits && default_config_s >= s_bits) {
                return checkConfig;
            }
        }
        return configs[0];
    }



    private int[] getConfigSpec(GLESVersion version) {
        final int redSize = mColorSpec.getRedSize();
        final int blueSize = mColorSpec.getBlueSize();
        final int greenSize = mColorSpec.getGreenSize();
        final int alphaSize = mColorSpec.getAlphaSize();
        final int depthSize = mDepthEnable ? 16 : 0;
        final int stencilSize = mStencilEnable ? 8 : 0;
        List<Integer> result = new ArrayList<Integer>();

        if (version == GLESVersion.OpenGLES20) {
            // set ES 2.0
            result.add(EGL10.EGL_RENDERABLE_TYPE);
            result.add(4); /* EGL_OPENGL_ES2_BIT */
        }

        result.add(EGL10.EGL_RED_SIZE);
        result.add(redSize);
        result.add(EGL10.EGL_GREEN_SIZE);
        result.add(greenSize);
        result.add(EGL10.EGL_BLUE_SIZE);
        result.add(blueSize);

        if (alphaSize > 0) {
            result.add(EGL10.EGL_ALPHA_SIZE);
            result.add(alphaSize);
        }
        if (depthSize > 0) {
            result.add(EGL10.EGL_DEPTH_SIZE);
            result.add(depthSize);
        }
        if (stencilSize > 0) {
            result.add(EGL10.EGL_STENCIL_SIZE);
            result.add(stencilSize);
        }
        // end
        result.add(EGL10.EGL_NONE);

        int[] result_array = new int[result.size()];
        for (int i = 0; i < result.size(); ++i) {
            result_array[i] = result.get(i);
        }
        return result_array;
    }

    private static int getConfigAttrib(EGL10 egl, EGLDisplay eglDisplay, EGLConfig eglConfig, int attr) {
        int[] value = new int[1];
        egl.eglGetConfigAttrib(eglDisplay, eglConfig, attr, value);
        return value[0];
    }
}
