package org.learn.test.ndkgl;

/**
 * Created by zzr on 2017/10/30.
 */

public class libJNI2GL {

    static {
        System.loadLibrary("opengl-lib");
    }

    public static native void init(int width, int height);
    public static native void drawOnNDK();
    public static native void setTextures(int[] textures);
}
