package org.learn.test.grafika;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import org.learn.test.PermissionUtils;
import org.learn.test.R;
import org.learn.test.grafika.util.AboutDialog;
import org.learn.test.grafika.util.ContentManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by zzr on 2017/11/9.
 */

public class GrafikaMainActivity extends ListActivity {
    public static final String TAG = "Grafika";

    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String CLASS_NAME = "class_name";

    /**
     * Each entry has three strings: the test title, the test description, and the name of
     * the activity class.
     */
    private static final String[][] TESTS = {
            { "* Play video (TextureView)",
                    "Plays .mp4 videos created by TextureView",
                    "PlayMovieActivity" },
            { "Continuous capture",
                    "Records camera continuously, saves a snapshot when requested",
                    "ContinuousCaptureActivity" },
            { "Double decode",
                    "Decodes two videos side-by-side",
                    "DoubleDecodeActivity" },
            { "Hardware scaler exerciser",
                    "Exercises SurfaceHolder#setFixedSize()",
                    "HardwareScalerActivity" },
            { "Live camera (TextureView)",
                    "Trivially feeds the camera preview to a view",
                    "LiveCameraActivity" },
            { "Multi-surface test",
                    "Three overlapping SurfaceViews, one secure",
                    "MultiSurfaceActivity" },
            { "Play video (SurfaceView)",
                    "Plays .mp4 videos created by SurfaceView",
                    "PlayMovieSurfaceActivity" },
            { "Record GL app",
                    "Records GL app with FBO, re-render, or FB blit",
                    "RecordFBOActivity" },
            { "Scheduled swap",
                    "Exercises SurfaceFlinger PTS handling",
                    "ScheduledSwapActivity" },
            { "Show + record camera",
                    "Shows camera preview, records when requested",
                    "CameraRecordActivity" },
            { "Simple GL in TextureView",
                    "Renders with GL as quickly as possible",
                    "TextureViewGLActivity" },
            { "Simple Canvas in TextureView",
                    "Renders with Canvas as quickly as possible",
                    "TextureViewCanvasActivity" },
            { "Texture from Camera",
                    "Resize and zoom the camera preview",
                    "TextureFromCameraActivity" },
            { "{bench} glReadPixels speed test",
                    "Tests glReadPixels() performance with 720p frames",
                    "ReadPixelsActivity" },
            { "{bench} glTexImage2D speed test",
                    "Tests glTexImage2D() performance on 512x512 image",
                    "TextureUploadActivity" },
            { "{util} Color bars",
                    "Shows RGB color bars",
                    "ColorBarActivity" },
            { "{util} OpenGL ES info",
                    "Dumps info about graphics drivers",
                    "GlesInfoActivity" },
            { "{~ignore} Chor test",
                    "Exercises bug",
                    "ChorTestActivity" },
            { "{~ignore} Codec open test",
                    "Exercises bug",
                    "CodecOpenActivity" },
            { "{~ignore} Software input surface",
                    "Exercises bug",
                    "SoftInputSurfaceActivity" },
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.grafika_activity_main);

        PermissionUtils.requestMultiPermissions(this, mPermissionGrant);

        // One-time singleton initialization; requires activity context to get file location.
        ContentManager.initialize(this);

        setListAdapter(new SimpleAdapter(this, createActivityList(),
                android.R.layout.two_line_list_item,
                new String[] { TITLE, DESCRIPTION },
                new int[] { android.R.id.text1, android.R.id.text2 } ));

        ContentManager cm = ContentManager.getInstance();
        if (!cm.isContentCreated(this)) {
            ContentManager.getInstance().createAll(this);
        }
    }

    /**
     * Compares two list items.
     */
    private static final Comparator<Map<String, Object>> TEST_LIST_COMPARATOR =
            new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> map1, Map<String, Object> map2) {
                    String title1 = (String) map1.get(TITLE);
                    String title2 = (String) map2.get(TITLE);
                    return title1.compareTo(title2);
                }
            };

    /**
     * Creates the list of activities from the string arrays.
     */
    private List<Map<String, Object>> createActivityList() {
        List<Map<String, Object>> testList = new ArrayList<Map<String, Object>>();

        for (String[] test : TESTS) {
            Map<String, Object> tmp = new HashMap<String, Object>();
            tmp.put(TITLE, test[0]);
            tmp.put(DESCRIPTION, test[1]);
            Intent intent = new Intent();
            try {
                Class cls = Class.forName("org.learn.test.grafika." + test[2]);
                intent.setClass(this, cls);
                tmp.put(CLASS_NAME, intent);
            } catch (ClassNotFoundException cnfe) {
                //throw new RuntimeException("Unable to find " + test[2], cnfe);
                cnfe.printStackTrace();
            }
            testList.add(tmp);
        }
        Collections.sort(testList, TEST_LIST_COMPARATOR);

        return testList;
    }

    @Override
    protected void onListItemClick(ListView listView, View v, int position, long id) {
        Map<String, Object> map = (Map<String, Object>)listView.getItemAtPosition(position);
        Intent intent = (Intent) map.get(CLASS_NAME);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    /**
     * onClick handler for "about" menu item.
     */
    public void clickAbout(@SuppressWarnings("unused") MenuItem unused) {
        AboutDialog.display(this);
    }
    /**
     * onClick handler for "regenerate content" menu item.
     */
    public void clickRegenerateContent(@SuppressWarnings("unused") MenuItem unused) {
        ContentManager.getInstance().createAll(this);
    }






    private PermissionUtils.PermissionGrant mPermissionGrant = new PermissionUtils.PermissionGrant() {

        @Override
        public void onPermissionGranted(int requestCode) {
            switch (requestCode) {
                case PermissionUtils.CODE_RECORD_AUDIO:
                    Toast.makeText(GrafikaMainActivity.this, "Result Permission Grant CODE_RECORD_AUDIO", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_GET_ACCOUNTS:
                    Toast.makeText(GrafikaMainActivity.this, "Result Permission Grant CODE_GET_ACCOUNTS", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_READ_PHONE_STATE:
                    Toast.makeText(GrafikaMainActivity.this, "Result Permission Grant CODE_READ_PHONE_STATE", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_CALL_PHONE:
                    Toast.makeText(GrafikaMainActivity.this, "Result Permission Grant CODE_CALL_PHONE", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_CAMERA:
                    Toast.makeText(GrafikaMainActivity.this, "Result Permission Grant CODE_CAMERA", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_ACCESS_FINE_LOCATION:
                    Toast.makeText(GrafikaMainActivity.this, "Result Permission Grant CODE_ACCESS_FINE_LOCATION", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_ACCESS_COARSE_LOCATION:
                    Toast.makeText(GrafikaMainActivity.this, "Result Permission Grant CODE_ACCESS_COARSE_LOCATION", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_READ_EXTERNAL_STORAGE:
                    Toast.makeText(GrafikaMainActivity.this, "Result Permission Grant CODE_READ_EXTERNAL_STORAGE", Toast.LENGTH_SHORT).show();
                    break;
                case PermissionUtils.CODE_WRITE_EXTERNAL_STORAGE:
                    Toast.makeText(GrafikaMainActivity.this, "Result Permission Grant CODE_WRITE_EXTERNAL_STORAGE", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        }
    };
}
