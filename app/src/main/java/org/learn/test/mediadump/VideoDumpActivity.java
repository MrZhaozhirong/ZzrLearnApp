package org.learn.test.mediadump;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.MediaController;

public class VideoDumpActivity extends Activity {

    private Context context;
    private View mainView;
    private VideoDumpView mVideoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        context = this;

        mainView = createView();
        setContentView(mainView);
    }


    private View createView() {
        mVideoView = new VideoDumpView(this);
        MediaController mediaController = new MediaController(context);
        mVideoView.setMediaController(mediaController);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.addView(mVideoView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        return mainLayout;
    }

    protected void onStop() {
        if (mVideoView != null) {
            if (mVideoView.isPlaying()) {
                mVideoView.stopPlayback();
            }
        }
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mVideoView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mVideoView.onResume();
    }
}
