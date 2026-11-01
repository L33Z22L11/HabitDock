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
        ComponentName normalTarget = target(pm, normal);
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
                // Vendor Settings may publish an alias for the very same activity.
                // Offering its target as "native" only opens the same page twice.
                if (new ComponentName(info.packageName, info.targetActivity == null ? info.name : info.targetActivity)
                        .equals(normalTarget))
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

    private static ComponentName target(PackageManager pm, ComponentName component) {
        if (component == null)
            return null;
        try {
            ActivityInfo info = pm.getActivityInfo(component, 0);
            return new ComponentName(info.packageName, info.targetActivity == null ? info.name : info.targetActivity);
        } catch (PackageManager.NameNotFoundException ignored) {
            return component;
        }
    }
}
