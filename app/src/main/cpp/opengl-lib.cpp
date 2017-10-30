//
// Created by nicky on 2017/10/30.
//
#include <jni.h>
#include <android/log.h>

#include <GLES2/gl2.h>
#include <stdlib.h>

#define  LOG_TAG    "GL_NDK"
#define  LOG_D(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOG_E(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void printGLString(const char *name, GLenum s) {
    const char *v = (const char *) glGetString(s);
    LOG_D("GL %s = %s\n", name, v);
}

static void checkGlError(const char* op) {
    for (GLint error = glGetError(); error; error = glGetError()) {
        LOG_D("after %s() glError (0x%x)\n", op, error);
    }
}

static const char gVertexShader[] =
        "attribute vec4 vPosition;\n"
                "attribute vec2 vTexCoords;\n"
                "varying vec2 colorVarying;\n"
                "void main() {\n"
                "  gl_Position = vPosition;\n"
                "  colorVarying = vTexCoords;\n"
                "}\n";

static const char gFragmentShader[] =
        "precision mediump float;\n"
                "varying vec2 colorVarying;\n"
                "uniform sampler2D sampler;\n"
                "void main() {\n"
                "  //gl_FragColor = vec4(0.0, 1.0, 0.0, 1.0);\n"
                "gl_FragColor = texture2D(sampler,colorVarying);\n"
                "}\n";

GLuint * mTexture;
GLuint gProgram;
GLint gvPositionHandle;
GLint gvTexCoorHandle;


GLuint loadShader(GLenum shaderType, const char* pSource) {
    GLuint shader = glCreateShader(shaderType);
    if (shader) {
        glShaderSource(shader, 1, &pSource, NULL);
        glCompileShader(shader);
        GLint compiled = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
        if (!compiled) {
            GLint infoLen = 0;
            glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &infoLen);
            if (infoLen) {
                char* buf = (char*) malloc(infoLen);
                if (buf) {
                    glGetShaderInfoLog(shader, infoLen, NULL, buf);
                    LOG_E("Could not compile shader %d:\n%s\n",
                         shaderType, buf);
                    free(buf);
                }
                glDeleteShader(shader);
                shader = 0;
            }
        }
    }
    return shader;
}
GLuint createProgram(const char* pVertexSource, const char* pFragmentSource) {
    GLuint vertexShader = loadShader(GL_VERTEX_SHADER, pVertexSource);
    if (!vertexShader) {
        return 0;
    }

    GLuint pixelShader = loadShader(GL_FRAGMENT_SHADER, pFragmentSource);
    if (!pixelShader) {
        return 0;
    }

    GLuint program = glCreateProgram();
    if (program) {
        glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");
        glAttachShader(program, pixelShader);
        checkGlError("glAttachShader");
        glLinkProgram(program);
        GLint linkStatus = GL_FALSE;
        glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);
        if (linkStatus != GL_TRUE) {
            GLint bufLength = 0;
            glGetProgramiv(program, GL_INFO_LOG_LENGTH, &bufLength);
            if (bufLength) {
                char* buf = (char*) malloc(bufLength);
                if (buf) {
                    glGetProgramInfoLog(program, bufLength, NULL, buf);
                    LOG_E("Could not link program:\n%s\n", buf);
                    free(buf);
                }
            }
            glDeleteProgram(program);
            program = 0;
        }
    }
    return program;
}

const GLfloat gTriangleVertices[] = { 0.0f, 0.5f,
                                      -0.5f, -0.5f,
                                      0.5f, -0.5f };
const GLfloat gTexCoor[] = { 0.5f,0,
                             0,1,
                             1,1 };

void renderFrame() {
    static float grey;
    grey += 0.01f;
    if (grey > 1.0f) {
        grey = 0.0f;
    }
    glClearColor(grey, grey, grey, 1.0f);
    checkGlError("glClearColor");
    glClear( GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
    checkGlError("glClear");
    glUseProgram(gProgram);
    checkGlError("glUseProgram");
    glVertexAttribPointer(gvPositionHandle, 2, GL_FLOAT, GL_FALSE, 0, gTriangleVertices);
    checkGlError("glVertexAttribPointer");
    glEnableVertexAttribArray(gvPositionHandle);
    checkGlError("glEnableVertexAttribArray");
    glVertexAttribPointer(gvTexCoorHandle, 2, GL_FLOAT, GL_FALSE, 0, gTexCoor);
    checkGlError("glVertexAttribPointer");
    glEnableVertexAttribArray(gvTexCoorHandle);
    checkGlError("glEnableVertexAttribArray");
    glActiveTexture(GL_TEXTURE0);
    checkGlError("glActiveTexture");
    glBindTexture(GL_TEXTURE_2D,mTexture[0]);
    checkGlError("glBindTexture");
    glDrawArrays(GL_TRIANGLES, 0, 3);
    checkGlError("glDrawArrays");
}




extern "C"
{
    JNIEXPORT void JNICALL Java_org_learn_test_ndkgl_libJNI2GL_init(JNIEnv * env, jobject obj,  jint width, jint height);
    JNIEXPORT void JNICALL Java_org_learn_test_ndkgl_libJNI2GL_drawOnNDK(JNIEnv * env, jobject obj);
    JNIEXPORT void JNICALL Java_org_learn_test_ndkgl_libJNI2GL_setTextures(JNIEnv * env, jobject obj,jintArray texture);
};

JNIEXPORT void JNICALL
Java_org_learn_test_ndkgl_libJNI2GL_init(JNIEnv *env, jclass type, jint width, jint height)
{

    printGLString("Version", GL_VERSION);
    printGLString("Vendor", GL_VENDOR);
    printGLString("Renderer", GL_RENDERER);
    printGLString("Extensions", GL_EXTENSIONS);

    LOG_D("setupGraphics(%d, %d)", width, height);
    gProgram = createProgram(gVertexShader, gFragmentShader);
    if (!gProgram) {
        LOG_E("Could not create program.");
        return ;
    }

    gvPositionHandle = glGetAttribLocation(gProgram, "vPosition");
    checkGlError("glGetAttribLocation");
    LOG_D("glGetAttribLocation(\"vPosition\") = %d\n", gvPositionHandle);

    gvTexCoorHandle = glGetAttribLocation(gProgram, "vTexCoords");
    checkGlError("glGetAttribLocation");
    LOG_D("glGetAttribLocation(\"vTexCoords\") = %d\n", gvTexCoorHandle);

    glViewport(0, 0, width, height);
    checkGlError("glViewport");
    return ;
}

JNIEXPORT void JNICALL
Java_org_learn_test_ndkgl_libJNI2GL_drawOnNDK(JNIEnv *env, jclass type)
{

    renderFrame();
}

JNIEXPORT void JNICALL
Java_org_learn_test_ndkgl_libJNI2GL_setTextures(JNIEnv *env, jclass type, jintArray textures_)
{
    //jint *textures = env->GetIntArrayElements(textures_, NULL);
    mTexture = (GLuint *)env->GetIntArrayElements(textures_,0);
    //env->ReleaseIntArrayElements(textures_, textures, 0);
}