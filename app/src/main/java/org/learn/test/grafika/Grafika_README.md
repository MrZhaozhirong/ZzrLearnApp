Grafika
=======

Welcome to Grafika, a dumping ground for Android graphics & media hacks.

Grafika is:
- A collection of hacks exercising graphics features.
- 一个图形特性的高超运用集合
- An SDK app, developed for API 18 (Android 4.3).  While some of the code
  may work with older versions of Android, no effort will be made to
  support them.
- 基于API 18 (Android 4.3)以上，以下版本的将不再支持
- Open source (Apache 2 license), copyright by Google.  So you can use the
  code according to the terms of the license (see "LICENSE").
- A perpetual work-in-progress.  It's updated whenever the need arises.
- 进行中的永久性工作。每当需要时，它都会更新。

However:
- It's not stable.
- 不稳定
- It's not polished or well tested.  Expect the UI to be ugly and awkward.
- 不是发布版，没有很好的测试。特别是UI丑陋而笨拙。
- It's not intended as a demonstration of the proper way to do things.
  The code may handle edge cases poorly or not at all.  Logging is often
  left enabled at a moderately verbose level.
- 这并不是为了证明做事情的正确方法。
  该代码可能处理边缘情况不佳或根本没有。日志通常
  在适度的详细级别上启用左。
- It's barely documented.
- 几乎没有文件记录。
- It's not part of the Android Open Source Project.  We cannot accept
  contributions to Grafika, even if you have an AOSP CLA on file.
- It's NOT AN OFFICIAL GOOGLE PRODUCT.  It's just a bunch of stuff that
  got thrown together on company time and equipment.
- It's generally just not supported.

To some extent, Grafika can be treated as a companion to the
[Android System-Level Graphics Architecture](http://source.android.com/devices/graphics/architecture.html)
document.  The doc explains the technology that the examples rely on, and uses some of
Grafika's activities as examples.  If you want to understand how the code here works, start
by reading that.
在某种程度上，Grafika可被视为[Android System-Level Graphics Architecture]
(http://source.android.com/devices/graphics/architecture.html)的配套说明文档，文档介绍了Grafika例子所依赖的技术，
如果你想搞明白代码是怎样运作起来的，阅读上面的文档吧。

There is some overlap with the code on http://www.bigflake.com/mediacodec/.
The code there largely consists of "headless" CTS tests, which are designed
to be robust, self-contained, and largely independent of the usual app
lifecycle issues.  Grafika is a conventional app, and makes an effort to
handle app issues correctly (like not doing lots of work on the UI thread).
有一些代码与这里（http://www.bigflake.com/mediacodec/.）重叠
这里的代码主要由“无头”CTS测试组成，这些测试设计成健壮的、自足的，并且基本上独立于通常的应用程序的生命周期问题。

Features are added to Grafika as the need arises, often in response to
developer complaints about correctness or performance problems in the
platform (either to confirm that the problems exist, or demonstrate an
approach that works).
功能被添加到Grafika的需要，往往是为了解决开发人员在平台抱怨的 正确性或性能问题
（要么确认问题存在，要么证明行之有效的方法）。

There are two areas where some amount of care is taken:
有两个方面需要我们花时间去多留意的
- Thread safety.  It's really easy to get threads crossed in subtly dangerous ways when
  working with the media classes.  (Read the
  [Android SMP Primer](http://developer.android.com/training/articles/smp.html)
  for a detailed introduction to the problem.)  GL/EGL's reliance on thread-local storage
  doesn't help.  Threading issues are frequently called out in comments in the source code.
- 线程安全。当操作媒体类，很容易会发生隐晦的线程错误危险，或者留有隐患
  关于这个问题的详细介绍，请阅读[Android SMP Primer](http://developer.android.com/training/articles/smp.html)
  GL/EGL的依赖线程局部存储是没有作用的。线程问题经常在源代码中的注释中被提及到。
- Garbage collection.  GC pauses cause jank.  Ideally, none of the activities will do any
  allocations while in a "steady state".  Allocations may occur while changing modes,
  e.g. starting or stopping recording.
- 垃圾回收，jank导致gc的停止。理想的，任何活动都不会在“稳定状态”下进行任何分配。
  再分配可能发生在模式转变的时候，例如开始或停止录制。

All code is written in the Java programming language -- the NDK is not used.

The first time Grafika starts, two videos are generated (gen-eight-rects, gen-sliders).
If you want to experiment with the generation code, you can cause them to be re-generated
from the main activity menu ("Regenerate content").
当Grafika第一次启动，会生成两个视频。（生成8个矩形，生成一个滑块）
如果您想对生成代码进行实验，可以使它们重新生成。从主活动菜单("Regenerate content")
Some features of Grafika may not work on an emulator, notably anything that involves
encoding video.  The problem is that the software AVC encoder doesn't support
`createInputSurface()`, and all of Grafika's video encoding features currently make
use of that.  Very little works in the AOSP emulator, even with `-gpu on`.
一些特性在模拟器上是不能工作的，特别是涉及到视频编码的任何事情。


Current features
----------------

[* Play video (TextureView)](src/com/android/grafika/PlayMovieActivity.java).
- Plays the video track from an MP4 file.
- Only sees files in `/data/data/com.android.grafika/files/`.  All of the activities that
  create video leave their files there.  You'll also find two automatically-generated videos
  (gen-eight-rects.mp4 and gen-slides.mp4).
- 只会在 `/data/data/com.android.grafika/files/`路径下查看MP4文件，全部activities创建出来的视频都是放在这个路径下。
  你也可以找到两个自动生成的视频文件(gen-eight-rects.mp4 and gen-slides.mp4).
- By default the video is played once, at the same rate it was recorded.  You can use the
  checkboxes to loop playback and/or play the frames at a fixed rate of 60 FPS.
- 默认情况下，视频播放一次，以同样的速率录制。你可以使用复选框来循环播放和/或在一个固定的速度每秒60帧的播放帧。
- Uses a `TextureView` for output.
- 使用TextureView作为输出
- Name starts with an asterisk so it's at the top of the list of activities.
- 名称以星号开头，所以它位于活动列表的顶部。

[Continuous capture](src/com/android/grafika/ContinuousCaptureActivity.java).
- Stores video in a circular buffer, saving it when you hit the "capture" button.  (Formerly "Constant capture".)
- 将视频存储在循环缓冲区中，当您点击“捕获”按钮时保存它。
- Currently hard-wired to try to capture 7 seconds of video from the camera at 6MB/sec,
  preferrably 15fps 720p.  That requires a buffer size of about 5MB.
- 目前在线从摄像机试图捕捉7秒的视频 6MB/sec，最好是15fps 720p。这需要一个大小5mb的缓冲区。
- The time span of frames currently held in the buffer is displayed.  The actual
  time span saved when you hit "capture" will be slightly less than what is shown because
  we have to start the output on a sync frame, which are configured to appear once per second.
- 容纳在缓冲中的每帧视频的时间间隔会被显示出来，你按“捕获”的总时间搓会比实际展示的少，
  因为我们必须在同步帧上启动输出，该帧被配置为每秒出现一次。
- Output is a video-only MP4 file ("constant-capture.mp4").  Video is always 1280x720, which
  usually matches what the camera provides; if it doesn't, the recorded video will have the
  wrong aspect ratio.
- 输出是一个视频MP4文件（“常数捕获MP4”）。视频是1280x720，这通常与相机提供的匹配；
  如果没有，则录制的视频将具有错的长宽比。

[Double decode](src/com/android/grafika/DoubleDecodeActivity.java).
- Decodes two video streams side-by-side to a pair of `TextureViews`.
- 解码两个视频流，会并排的以一对 textureviews 展示出来。
- Plays the two auto-generated videos.  Note they play at different rates.
- 播放两个自动生成的视频。注意它们以不同的速率播放。
- The video decoders don't stop when the screen is rotated.  We retain the `SurfaceTexture`
  and just attach it to the new `TextureView`.  Useful for avoiding expensive codec reconfigures.
  The decoders *do* stop if you leave the activity, so we don't tie up hardware codec
  resources indefinitely.  (It also doesn't stop if you turn the screen off with the power
  button, which isn't good for the battery, but might be handy if you're feeding an external
  display or your player also handles audio.)
- 当屏幕旋转时，视频解码器不会停止。我们保留`SurfaceTexture` 只是将它附加到新的` textureview `，用于避免昂贵的编解码重构。
  如果您离开活动，解码器将停止，因此我们不会无限期地绑定硬件编解码器资源。
  (它也不会停止，即使你关掉屏幕的开关按钮，这是不适合电池，但可能是方便的，如果你是外部提供显示或您的播放器也处理音频。)
- Unlike most activities in Grafika, this provides different layouts for portrait and landscape.
  The videos are scaled to fit.
- 不像Grafika的大部分activities，这为竖屏和横屏提供了不同的布局。视频缩放到适合。

[Hardware scaler exerciser](src/com/android/grafika/HardwareScalerActivity.java).
- Shows GL rendering with on-the-fly surface size changes.
- 显示随表面尺寸变化的GL渲染。
- The motivation behind the feature this explores is described in a developer blog post:
  http://android-developers.blogspot.com/2013/09/using-hardware-scaler-for-performance.html
- 此特性背后的动机是在开发人员博客文章中描述的：url
- You will see one frame rendered incorrectly when changing sizes.  This is because the
  render size is adjusted in the "surface changed" callback, but the surface's size doesn't
  actually change until we latch the next buffer.  This is straightforward to fix (left as
  an exercise for the reader).
- 当更改大小时，您会看到一个帧显示不正确。这是因为渲染大小是在“更改表面”回调中调整的，
  但是在我们锁定下一个缓冲区之前，表面的大小实际上不会改变。这是很容易解决的。


[Live camera (TextureView)](src/com/android/grafika/LiveCameraActivity.java).  Directs the camera preview to a `TextureView`.
- This comes more or less verbatim from the [TextureView](http://developer.android.com/reference/android/view/TextureView.html) documentation.
- Uses the default (rear-facing) camera.  If the device has no default camera (e.g.Nexus 7 (2012)), the Activity will crash.
- 使用默认（后置）摄像机。如果设备没有默认的相机（e.g.nexus 7（2012）），该活动将崩溃。

[Multi-surface test](src/com/android/grafika/MultiSurfaceTest.java).
- Simple activity with three overlapping SurfaceViews, one marked secure.
- 一个简答的例子-三重叠的SurfaceView，一个显著的安全。
- Useful for examining HWC behavior with multiple static layers, and
  screencap / screenrecord behavior with a secure surface.  (If you record the screen one
  of the circles should be missing, and capturing the screen should just show black.)
- 对于多个静态层HareWareComposer行为研究，包括 一个安全surface的屏幕捕捉/录制的行为
  （如果你录下了屏幕，其中一个圆圈应该不见了，而捕捉屏幕应该只显示黑色。）
- If you tap the "bounce" button, the circle on the non-secure layer will animate.  It will
  update as quickly as possible, which may be slower than the display refresh rate because
  the circle is rendered in software.  The frame rate will be reported in logcat.
- 如果你点击“反弹”按钮，非安全层上的圆会产生动画效果。它将尽可能快地更新，这可能比显示刷新速率慢。
  因为圆圈是用软件渲染的。帧速率将报告在logcat。

[Play video (SurfaceView)](src/com/android/grafika/PlayMovieSurfaceActivity.java).
- Plays the video track from an MP4 file.
- Works very much like "Play video (TextureView)", though not all features are present.
  See the class comment for a list of advantages to using SurfaceView.
- 与"Play video (TextureView)"工作很是相像，虽然不是所有特性都出现。
  看看 关于使用SurfaceView类评论优点列表就知道了。


[Record GL app](src/com/android/grafika/RecordFBOActivity.java).
- Simultaneously draws to the display and to a video encoder with OpenGL ES, using framebuffer objects to avoid re-rendering.
- 同时用opengl绘制到显示器和视频编码器，使用fbo避免重新绘制。
- It can write to the video encoder three different ways: (1) draw twice; (2) draw offscreen and
  blit twice; (3) draw onscreen and blit framebuffer.  #3 doesn't work yet.
- 它可以以三种不同的方式向视频编码器写入：（1）绘制的两倍；（2）画一次离屏的，激活两次（3）绘制在屏幕上和激活 帧缓存。
- The renderer is trigged by Choreographer to update every vsync.  If we get too far behind,
  we will skip frames.  This is noted by an on-screen drop counter and a border flash.  You
  generally won't see any stutter in the animation, because we don't skip the object
  movement, just the render.
- 渲染器是由Choreographer触发更新每个垂直同步。如果我们落后得太远，我们会跳过这些帧。
  这些都是由一个屏幕上的计数器去记录，而且每次跳帧都会出现一次边界闪光。你一般不会看到任何停顿的动画
  因为我们不是跳过对象运作流程，只是跳过渲染而已。
- The encoder is fed every-other frame, so the recorded output will be ~30fps rather than ~60fps
  on a typical device.
- 编码器被馈送到其他帧，所以在一个典型装置上记录的输出将会是30fps浮动而不是60fps
- The recording is letter- or pillar-boxed to maintain an aspect ratio that matches the
  display, so you'll get different results from recording in landscape vs. portrait.
- 录制的是文字或柱盒装，以保持一个长宽比相匹配的显示，所以你会在竖屏与横屏得到不同的录制结果。
- The output is a video-only MP4 file ("fbo-gl-recording.mp4").

[Scheduled swap](src/com/android/grafika/ScheduledSwapActivity.java).
- Exercises a SurfaceFlinger feature that allows you to submit buffers to be displayed at a specific time.
- 练习 SurfaceFlinger 功能，允许你提交的缓冲区是在特定的时间显示。
- Requires API 19 (Android 4.4 "KitKat") to do what it's supposed to.  The current implementation
  doesn't really look any different on API 18 to the naked eye.
- 需要API 19（Android 4.4“奇巧”）做什么是应该的。目前的实现在API 18上与肉眼没有什么不同。
- You can configure the frame delivery timing (e.g. 24fps uses a 3-2 pattern) and how far
  in advance frames are scheduled.  Selecting "ASAP" disables scheduling.
- 你可以配置帧传送时间（例如24fps采用3:2模式）和提前多久预定帧。选择“尽快”禁用调度。
- Use systrace with tags `sched gfx view --app=com.android.grafika` to observe the effects.
- 使用Systrace标签` sched gfx view --app=com.android.grafika `观察效果。
- The moving square changes colors when the app is unhappy about timing.
- 当应用程序对计时不满意时，移动方块会改变颜色。

[Show + capture camera](src/com/android/grafika/CameraCaptureActivity.java).
- Attempts to record at 720p from the front-facing camera, displaying the preview and recording it simultaneously.
- 试图从前置摄像头录制720P，同时地，显示预览和记录。
- Use the record button to toggle recording on and off.
- 使用“记录”按钮切换记录。
- Recording continues until stopped.  If you back out and return, recording will start again,
  with a real-time gap.  If you try to play the movie while it's recording, you will see
  an incomplete file (and probably cause the play movie activity to crash).
- 录制一直持续到停止。录音将再次开始，以实时的间隙。如果你试着在录音时播放这个视频，你会看到一个不完整的文件
  （可能导致播放器活动崩溃）
- The recorded video is scaled to 640x480, so it will probably look squished.  A real app
  would either set the recording size equal to the camera input size, or correct the aspect
  ratio by letter- or pillar-boxing the frames as they are rendered to the encoder.
- 录制的视频缩放为640x480，所以它可能看起来压扁。一个真正的应用程序可以设置与摄像机输入大小相等的录制大小。
  或者通过字母或柱拳来校正纵横比，当它们被呈现给编码器时。
- You can select a filter to apply to the preview.  It does not get applied to the recording.
  The shader used for the filters is not optimized, but seems to perform well on most devices
  (the original Nexus 7 (2012) being a notable exception).  Demo
  here: http://www.youtube.com/watch?v=kH9kCP2T5Gg
- 可以选择要应用于预览的筛选器。它并不适用于录制。用于过滤器的着色器没有优化，但在大多数设备上表现很好。
  Demo 在这里http://www.youtube.com/watch?v=kH9kCP2T5Gg
- The output is a video-only MP4 file ("camera-test.mp4").

[Simple Canvas in TextureView](src/com/android/grafika/TextureViewCanvasActivity.java).
- Exercises software rendering to a `TextureView` with a `Canvas`.
- 练习软件绘制一个` textureview `与`画布`。
- Renders as quickly as possible.  Because it's using software rendering, this will likely
  run more slowly than the "Simple GL in TextureView" activity.
- 尽可能快地渲染。因为它是使用软件渲染，这可能会运行得更慢比“简单的GL textureview”活动。
- Toggles the use of a dirty rect every 64 frames.  When enabled, the dirty rect extends
  horizontally across the screen.

[Simple GL in TextureView](src/com/android/grafika/TextureViewGLActivity.java).
- Demonstates simple use of GLES in a `TextureView`, rather than a `GLSurfaceView`.
- 这个程序演示了在一个` textureview `方程的简单使用，而不是一个` glsurfaceview `
- Renders as quickly as possible.  On most devices it will exceed 60fps and flicker wildly,
  but in 4.4 ("KitKat") a bug prevents the system from dropping frames.
- 尽可能快地渲染。在大多数设备将超过60fps和闪烁着，但在4.4（“KitKat”）一个bug防止了从系统的掉帧。

[Texture from Camera](src/com/android/grafika/TextureFromCameraActivity.java).
- Renders Camera preview output with a GLES texture.
- 使相机预览输出与对应的纹理。
- Adjust the sliders to set the size, rotation, and zoom.  Touch anywhere else to center
  the rect at the point of the touch.
- 调整滑块设置大小，旋转和缩放。触摸其他地方中心直接在触摸点。

[Color bars](src/com/android/grafika/ColorBarActivity.java).  Displays RGB color bars.

[OpenGL ES Info](src/com/android/grafika/GlesInfoActivity.java).
- Dumps version info and extension lists.
- 转储版本信息和扩展列表。
- The "Save" button writes a copy of the output to the app's file area.
- “保存”按钮将输出的一个副本写入应用程序的文件区域。

[glTexImage2D speed test](src/com/android/grafika/TextureUploadActivity.java).
Simple, unscientific measurement of the time required to upload a 512x512 RGBA texture with `glTexImage2D()`.

[glReadPixels speed test](src/com/android/grafika/ReadPixelsActivity.java).
Simple, unscientific measurement of the time required for `glReadPixels()` to read a 720p frame.


Known issues
------------

- Nexus 4 running Android 4.3 (JWR67E): "Show + capture camera" crashes if you select one of
  the filtered modes.  Appears to be a driver bug (Adreno "Internal compiler error").


Feature & fix ideas
-------------------

In no particular order.

- Stop using AsyncTask for anything where performance or latency matters.
- 停止使用AsyncTask任何性能或延迟的问题。
- Add a "fat bits" viewer for camera (single SurfaceView; left half has live camera feed
  and a pan rect, right half has 8x pixels)
- 添加一个“胖位”观察相机（单SurfaceView；左半部分直播摄像头的矩形，右半边有8x像素）
- Change the "Simple GL in TextureView" animation.  Or add an epilepsy warning.
- 改变 "Simple GL in TextureView" 的动画效果，或增加癫痫警告。
- Cross-fade from one video to another, recording the result.  Allow specification of
  the resolution (maybe QVGA, 720p, 1080p) and generate appropriately.
- 交叉淡入从一个视频到另一个，记录结果。允许的分辨率规格（也许QVGA，720P，1080P）和生成适当的。
- Add features to the video player, like a slider for random access, and buttons for
  single-frame advance / rewind (requires seeking to nearest sync frame and decoding frames
  until target is reached).
- 新增功能的视频播放器，像一个滑块随机访问，并为单帧前进/倒带按钮。
- Convert a series of PNG images to video.
- 将一系列PNG图像转换成视频。
- Use virtual displays to record app activity.
- 使用虚拟显示器记录应用程序活动。
- Play continuous video from a series of MP4 files with different characteristics.  Will
  probably require "preloading" the next movie to keep playback seamless.
- 从一系列具有不同特性的MP4文件中播放连续视频。可能需要“预加载”下一部电影，以保持无缝播放。
- Experiment with alternatives to glReadPixels().  Add a PBO speed test.  (Doesn't seem
  to be a way to play with eglCreateImageKHR from Java.)
- 与替代glReadPixels()实验。增加一个PBO速度测试。（似乎没有办法使用eglCreateImageKHR从java。）
- Do something with ImageReader class (req API 19).
- Figure out why "double decode" playback is sometimes janky.
- 解决为什么有时候janky会出现“两次解码”
- Add fps indicator to "Simple GL in TextureView".
- 在"Simple GL in TextureView".加入FPS指示器
- Capture audio from microphone, record + mux it.
- 从麦克风捕获音频，录制+多路复用器。
- Enable preview on front/back cameras simultaneously, display them side-by-side.  (This
  appears to be impossible except on specific devices.)
- 启用前/后摄像机预览，同时显示它们并排。（这似乎是不可能的，除非是在特定的设备上）。
- Add a test that renders to two different TextureViews using different EGLContexts
  from a single renderer thread.
- 添加测试使用从一个单一的渲染线程不同的eglcontexts不同textureviews。
