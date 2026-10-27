package dev.habitdock;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;

/**
 * Only offer publicly accessible Settings activities; never launch private
 * fragments.
 */
final class NativeAppDetails {
    static Intent find(Context context, String pkg) {
        Intent standard = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        PackageManager pm = context.getPackageManager();
        ComponentName normal = standard.resolveActivity(pm);
        String[] names = {"com.android.settings.Settings$AppInfoDashboardActivity",
                "com.android.settings.applications.InstalledAppDetailsTop",
                "com.android.settings.applications.InstalledAppDetailsActivity"};
        for (String name : names) {
            ComponentName component = new ComponentName("com.android.settings", name);
            if (component.equals(normal))
                continue;
            try {
                ActivityInfo info = pm.getActivityInfo(component, 0);
                if (!info.exported || !info.enabled || !info.applicationInfo.enabled)
                    continue;
                if (info.permission != null
                        && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED)
                    continue;
                return new Intent(standard).setComponent(component).putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return null;
    }
}
