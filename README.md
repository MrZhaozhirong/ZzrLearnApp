##### ZzrLearnApp 用于自己学习网上资料fork工程

1.VideoDump 官方demo 基于GLSurfaceView+MediaPlayer，其中glReadPixels的参数用的比较严谨（如下）

// So far, glReadPixels only supports two (format, type) combinations
//     GL_RGB  GL_UNSIGNED_SHORT_5_6_5   16 bits per pixel (default)
//     GL_RGBA GL_UNSIGNED_BYTE          32 bits per pixel
public static final int PIXEL_FORMAT = GLES20.GL_RGBA;
public static final int PIXEL_TYPE = PIXEL_FORMAT == GLES20.GL_RGBA
                                        ? GLES20.GL_UNSIGNED_BYTE : GLES20.GL_UNSIGNED_SHORT_5_6_5;
                                        
GLES20.glReadPixels(mStartX, mStartY, mWidth, mHeight,
                VideoDumpConfig.PIXEL_FORMAT,
                VideoDumpConfig.PIXEL_TYPE,
                mBuffer); 
                
还有就是glGetError的正确用法。。。到现在才知道
  private static void checkEglError(String prompt, EGL10 egl) {
        int error;
        while ((error = egl.eglGetError()) != EGL10.EGL_SUCCESS) {
            Log.e(TAG, String.format("%s: EGL error: 0x%x", prompt, error));
        }
    }
    
    
2.GLTextureView 原创是一个日本程序员，基于TextureView自带一个surfaceTexture，
  然后抽取EGL部分，在TextureView里增加一个渲染线程，代码很仔细区分了渲染线程的surface和系统的surface，
  跟着fork一次真的收获很大，才知道自己对Android的OpenGL的详细细节真的差太多了。
  
3.NDK版的GL，环境还是依赖于GLSurfaceview的。调用在NDK层，还是有点参考价值的。
