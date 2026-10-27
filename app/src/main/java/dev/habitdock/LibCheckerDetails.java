package dev.habitdock;

import android.content.*;
import android.content.pm.*;

/**
 * LibChecker's public SHOW_APP_INFO contract; independent of its internal
 * activity names.
 */
final class LibCheckerDetails {
    static Intent find(Context context, String packageName) {
        Intent intent = new Intent(Intent.ACTION_SHOW_APP_INFO)
                .setPackage("com.absinthe.libchecker")
                .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
        ResolveInfo resolved = context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        ActivityInfo info = resolved == null ? null : resolved.activityInfo;
        if (info == null || !info.exported || !info.enabled || !info.applicationInfo.enabled
                || !"com.absinthe.libchecker".equals(info.packageName))
            return null;
        if (info.permission != null
                && context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED)
            return null;
        return intent.setComponent(new ComponentName(info.packageName, info.name));
    }
}
