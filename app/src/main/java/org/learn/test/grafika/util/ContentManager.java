package org.learn.test.grafika.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.learn.test.R;
import org.learn.test.grafika.GrafikaMainActivity;
import org.learn.test.grafika.content.Content;
import org.learn.test.grafika.content.GeneratedMovie;
import org.learn.test.grafika.content.MovieEightRects;
import org.learn.test.grafika.content.MovieSliders;

import java.io.File;
import java.util.ArrayList;

/**
 * Created by zzr on 2017/11/9.
 */

public class ContentManager {
    private static final String TAG = GrafikaMainActivity.TAG;

    // Enumerated content tags.  These are used as indices into the mContent ArrayList,
    // so don't make them sparse.
    // TODO: consider using String tags and a HashMap?  prepare() is currently fragile,
    // depending on the movies being added in tag-order.  Could also just use a plain array.
    public static final int MOVIE_EIGHT_RECTS = 0;
    public static final int MOVIE_SLIDERS = 1;

    private static final int[] ALL_TAGS = new int[] {
            MOVIE_EIGHT_RECTS,
            MOVIE_SLIDERS
    };

    // Housekeeping.
    private static final Object sLock = new Object();
    private static ContentManager sInstance = null;

    private boolean mInitialized = false;
    private File mFilesDir;
    private ArrayList<Content> mContent;

    public static ContentManager getInstance() {
        synchronized (sLock) {
            if (sInstance == null) {
                sInstance = new ContentManager();
            }
            return sInstance;
        }
    }

    private ContentManager() {}

    public static void initialize(Context context) {
        ContentManager mgr = getInstance();
        synchronized (sLock) {
            if (!mgr.mInitialized) {
                mgr.mFilesDir = context.getFilesDir();
                mgr.mContent = new ArrayList<Content>();
                mgr.mInitialized = true;
            }
        }
    }

    /**
     * Returns the specified item.
     */
    public Content getContent(int tag) {
        synchronized (mContent) {
            return mContent.get(tag);
        }
    }
    /**
     * Prepares the specified item.
     * <p>
     * This may be called from the async task thread.
     */
    private void prepare(ProgressUpdater prog, int tag) {
        GeneratedMovie movie;
        switch (tag) {
            case MOVIE_EIGHT_RECTS:
                movie = new MovieEightRects();
                movie.create(getPath(tag), prog);
                synchronized (mContent) {
                    mContent.add(tag, movie);
                }
                break;
            case MOVIE_SLIDERS:
                movie = new MovieSliders();
                movie.create(getPath(tag), prog);
                synchronized (mContent) {
                    mContent.add(tag, movie);
                }
                break;
            default:
                throw new RuntimeException("Unknown tag " + tag);
        }
    }

    /**
     * Creates all content, overwriting any existing entries.
     * Call from main UI thread.
     */
    public void createAll(Activity caller) {
        prepareContent(caller, ALL_TAGS);
    }

    /**
     * Prepares the specified content.  For example, if the caller requires a movie that doesn't
     * exist, this will post a progress dialog and generate the movie.
     * Call from main UI thread.  This returns immediately.  Content generation continues
     * on a background thread.
     */
    public void prepareContent(Activity caller, int[] tags) {
        // Put up the progress dialog.
        AlertDialog.Builder builder = WorkDialog.create(caller, R.string.preparing_content);
        builder.setCancelable(false);
        AlertDialog dialog = builder.show();

        // Generate content in async task.
        GenerateTask genTask = new GenerateTask(caller, dialog, tags);
        genTask.execute();
    }

    /**
     * Performs generation of content on an async task thread.
     */
    private static class GenerateTask extends AsyncTask<Void, Integer, Integer>
        implements ProgressUpdater
    {
        // ----- accessed from UI thread -----
        private final Context mContext;
        private final AlertDialog mPrepDialog;
        private final ProgressBar mProgressBar;

        // ----- accessed from async thread -----
        private int mCurrentIndex;

        // ----- accessed from both -----
        private final int[] mTags;
        private volatile RuntimeException mFailure;

        public GenerateTask(Context context, AlertDialog dialog, int[] tags) {
            mContext = context;
            mPrepDialog = dialog;
            mTags = tags;
            mProgressBar = (ProgressBar) mPrepDialog.findViewById(R.id.work_progress);
            mProgressBar.setMax(tags.length * 100);
        }

        @Override // async task thread
        protected Integer doInBackground(Void... voids) {
            ContentManager contentManager = ContentManager.getInstance();
            Log.d(TAG, "doInBackground...");
            for (int i = 0; i < mTags.length; i++) {
                mCurrentIndex = i;
                updateProgress(0);
                try {
                    contentManager.prepare(this, mTags[i]);
                } catch (RuntimeException re) {
                    mFailure = re;
                    break;
                }
                updateProgress(100);
            }

            if (mFailure != null) {
                Log.w(TAG, "Failed while generating content", mFailure);
            } else {
                Log.d(TAG, "generation complete");
            }
            return 0;
        }

        @Override // async task thread
        public void updateProgress(int percent) {
            publishProgress(mCurrentIndex, percent);
        }

        @Override // UI thread
        protected void onProgressUpdate(Integer... progressArray) {
            int index = progressArray[0];
            int percent = progressArray[1];
            //Log.d(TAG, "progress " + index + "/" + percent + " of " + mTags.length * 100);
            if (percent == 0) {
                TextView name = (TextView) mPrepDialog.findViewById(R.id.workJobName_text);
                name.setText(ContentManager.getInstance().getFileName(mTags[index]));
            }
            mProgressBar.setProgress(index * 100 + percent);
        }

        @Override // UI thread
        protected void onPostExecute(Integer result) {
            Log.d(TAG, "onPostExecute -- dismss");
            mPrepDialog.dismiss();

            if (mFailure != null) {
                showFailureDialog(mContext, mFailure);
            }
        }

        /**
         * Posts an error dialog, including the message from the failure exception.
         */
        private void showFailureDialog(Context context, RuntimeException failure) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.contentGenerationFailedTitle);
            String msg = context.getString(R.string.contentGenerationFailedMsg,
                    failure.getMessage());
            builder.setMessage(msg);
            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                }
            });
            builder.setCancelable(false);
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }


    /**
     * Returns true if all of the content has been created.
     * If this returns false, call createAll.
     */
    public boolean isContentCreated(@SuppressWarnings("unused") Context unused) {
        // Ideally this would probe each individual item to see if anything needs to be done,
        // and a subsequent "prepare" call would generate only the necessary items.  This
        // takes a much simpler approach and just checks to see if the files exist.  If the
        // content changes the user will need to force a regen (via a menu option) or wipe data.
        //理想情况下，这将探测每个单独的项目，看看是否需要做什么，随后的“准备”调用将只生成必要的项。
        // 这需要一个简单得多的方法，只是检查文件是否存在。如果内容变化的用户将需要强制再生（通过菜单选项）或擦除数据。
        for (int i = 0; i < ALL_TAGS.length; i++) {
            File file = getPath(i);
            if (!file.canRead()) {
                Log.d(TAG, "Can't find readable " + file);
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the filename for the tag.
     */
    private String getFileName(int tag) {
        switch (tag) {
            case MOVIE_EIGHT_RECTS:
                return "gen-eight-rects.mp4";
            case MOVIE_SLIDERS:
                return "gen-sliders.mp4";
            default:
                throw new RuntimeException("Unknown tag " + tag);
        }
    }
    /**
     * Returns the storage location for the specified item.
     */
    public File getPath(int tag) {
        return new File(mFilesDir, getFileName(tag));
    }

    public interface ProgressUpdater {
        /**
         * Updates a progress meter.
         * @param percent Percent completed (0-100).
         */
        void updateProgress(int percent);
    }
}
