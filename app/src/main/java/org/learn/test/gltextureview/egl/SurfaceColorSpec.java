package org.learn.test.gltextureview.egl;

/**
 * Created by zzr on 2017/10/11.
 */

public enum SurfaceColorSpec {

    /**
     * RGBA各8bit
     */
    RGBA8888 {
        @Override
        public int getRedSize() {
            return 8;
        }

        @Override
        public int getGreenSize() {
            return 8;
        }

        @Override
        public int getBlueSize() {
            return 8;
        }

        @Override
        public int getAlphaSize() {
            return 8;
        }
    },

    /**
     * RGB各8bit
     */
    RGB888 {
        @Override
        public int getRedSize() {
            return 8;
        }

        @Override
        public int getGreenSize() {
            return 8;
        }

        @Override
        public int getBlueSize() {
            return 8;
        }

        @Override
        public int getAlphaSize() {
            return 0;
        }
    },

    RGB565 {
        @Override
        public int getRedSize() {
            return 5;
        }

        @Override
        public int getGreenSize() {
            return 6;
        }

        @Override
        public int getBlueSize() {
            return 5;
        }

        @Override
        public int getAlphaSize() {
            return 0;
        }
    };






    /**
     * R bits
     * @return
     */
    public abstract int getRedSize();

    /**
     * B bits
     * @return
     */
    public abstract int getBlueSize();

    /**
     * G bits
     * @return
     */
    public abstract int getGreenSize();

    /**
     * A bits
     * @return
     */
    public abstract int getAlphaSize();
}
