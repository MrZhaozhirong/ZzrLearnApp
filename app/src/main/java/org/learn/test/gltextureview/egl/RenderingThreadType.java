package org.learn.test.gltextureview.egl;

import org.learn.test.gltextureview.GLTextureView;

/**
 * Created by zzr on 2017/10/11.
 */

public enum RenderingThreadType {
    /**
     * Rendering on Background Loop
     */
    BackgroundThread,

    /**
     * Rendering on {@link GLTextureView#requestRendering()}
     */
    RequestThread,
}