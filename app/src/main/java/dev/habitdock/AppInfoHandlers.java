package dev.habitdock;

import android.content.*;
import android.content.pm.*;
import java.util.List;

/** Finds apps that accept Android's package information intent. */
final class AppInfoHandlers {
    static Intent find(Context context, String packageName) {
        Intent intent = new Intent(Intent.ACTION_SHOW_APP_INFO).putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> matches = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        ComponentName first = null;
        int count = 0;
        for (ResolveInfo match : matches) {
            ActivityInfo info = match.activityInfo;
            if (info == null || !info.exported || !info.enabled || !info.applicationInfo.enabled
                    || info.packageName.equals(context.getPackageName()))
                continue;
            if (info.permission != null
                    && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED)
                continue;
            if (first == null)
                first = new ComponentName(info.packageName, info.name);
            count++;
        }
        if (first == null)
            return null;
        if (count == 1)
            return intent.setComponent(first);
        return Intent.createChooser(intent, "打开应用信息");
    }
}
