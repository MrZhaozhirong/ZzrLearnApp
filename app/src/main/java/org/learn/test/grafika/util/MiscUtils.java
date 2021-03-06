package org.learn.test.grafika.util;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import org.learn.test.grafika.GrafikaMainActivity;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by zzr on 2017/11/13.
 */

public class MiscUtils {

    private static final String TAG = GrafikaMainActivity.TAG;

    private MiscUtils() {}

    /**
     * Obtains a list of files that live in the specified directory and match the glob pattern.
     */
    public static String[] getFiles(File dir, String glob) {
        String regex = globToRegex(glob);
        Log.e(TAG, "globToRegex "+regex);
        final Pattern pattern = Pattern.compile(regex);
        String[] result = dir.list(new FilenameFilter() {
            @Override public boolean accept(File dir, String name) {
                Matcher matcher = pattern.matcher(name);
                return matcher.matches();
            }
        });
        Arrays.sort(result);

        return result;
    }


    /**
     * Converts a filename globbing pattern to a regular expression.
     * <p>
     * The regex is suitable for use by Matcher.matches(), which matches the entire string, so
     * we don't specify leading '^' or trailing '$'.
     */
    private static String globToRegex(String glob) {
        // Quick, overly-simplistic implementation -- just want to handle something simple
        // like "*.mp4".
        //
        // See e.g. http://stackoverflow.com/questions/1247772/ for a more thorough treatment.
        StringBuilder regex = new StringBuilder(glob.length());
        //regex.append('^');
        for (char ch : glob.toCharArray()) {
            switch (ch) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append('.');
                    break;
                case '.':
                    regex.append("\\.");
                    break;
                default:
                    regex.append(ch);
                    break;
            }
        }
        //regex.append('$');
        return regex.toString();
    }






    /**
     * Obtains the approximate refresh time, in nanoseconds, of the default display associated
     * with the activity.
     * <p>
     * The actual refresh rate can vary slightly (e.g. 58-62fps on a 60fps device).
     */
    public static long getDisplayRefreshNsec(Activity activity) {
        Display display = ((WindowManager)
                activity.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        double displayFps = display.getRefreshRate();
        long refreshNs = Math.round(1000000000L / displayFps);
        Log.d(TAG, "refresh rate is " + displayFps + " fps --> " + refreshNs + " ns");
        return refreshNs;
    }
}
