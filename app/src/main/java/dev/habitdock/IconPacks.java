package dev.habitdock;

import android.content.*;
import android.content.pm.*;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Xml;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import org.xmlpull.v1.XmlPullParser;

/**
 * Reads installed ADW/Nova-compatible appfilter resources, without loading pack
 * code.
 */
// External appfilter names cannot use compile-time resource IDs.
@android.annotation.SuppressLint("DiscouragedApi")
final class IconPacks {
    static final String[] ACTIONS = {"org.adw.launcher.THEMES", "com.gau.go.launcherex.theme",
            "com.novalauncher.THEME", "com.anddoes.launcher.THEME"};
    static final class Pack {
        final String pkg, label;
        Pack(String pkg, String label) {
            this.pkg = pkg;
            this.label = label;
        }
    }
    private static String cachedKey = "";
    private static Mapping cached;
    private static String versionPackage, versionStamp;

    static List<Pack> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, Pack> packs = new HashMap<>();
        for (String action : ACTIONS) {
            List<ResolveInfo> matches = new ArrayList<>(pm.queryIntentActivities(new Intent(action), 0));
            matches.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(action), 0));
            for (ResolveInfo info : matches) {
                ApplicationInfo app = info.activityInfo.applicationInfo;
                packs.putIfAbsent(app.packageName, new Pack(app.packageName, app.loadLabel(pm).toString()));
            }
        }
        List<Pack> result = new ArrayList<>(packs.values());
        result.sort(Comparator.comparing(pack -> pack.label, java.text.Collator.getInstance()));
        return result;
    }

    static String label(Context c, String pkg) {
        if (pkg.isEmpty())
            return "应用原图标";
        try {
            return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0))
                    .toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return "图标包不可用 · 使用原图标";
        }
    }

    static synchronized String version(Context c, String pkg) {
        if (pkg.isEmpty())
            return "";
        if (!pkg.equals(versionPackage)) {
            versionPackage = pkg;
            try {
                PackageInfo info = c.getPackageManager().getPackageInfo(pkg, 0);
                versionStamp = pkg + ":" + info.lastUpdateTime;
            } catch (PackageManager.NameNotFoundException ignored) {
                versionStamp = pkg + ":missing";
            }
        }
        return versionStamp + ":" + LocalDate.now();
    }

    static synchronized void invalidate() {
        versionPackage = null;
        cachedKey = "";
        cached = null;
    }

    static final class Source {
        final Bitmap bitmap;
        final boolean themed;
        Source(Bitmap bitmap, boolean themed) {
            this.bitmap = bitmap;
            this.themed = themed;
        }
    }

    static Source loadSource(Context c, String app, String pack) throws PackageManager.NameNotFoundException {
        Drawable icon = drawable(c, app, pack);
        return new Source(WidgetIcons.source(icon == null ? c.getPackageManager().getApplicationIcon(app) : icon),
                icon != null);
    }

    static Bitmap source(Context c, String app, String pack) throws PackageManager.NameNotFoundException {
        return loadSource(c, app, pack).bitmap;
    }

    static Bitmap styled(Context c, String app, IconStyle style) throws PackageManager.NameNotFoundException {
        Source source = loadSource(c, app, style.pack);
        return WidgetIcons.style(c, source.bitmap, style, source.themed);
    }

    static Drawable drawable(Context c, String app, String pack) {
        if (pack.isEmpty())
            return null;
        Mapping mapping = mapping(c, pack);
        if (mapping == null)
            return null;
        Intent launch = c.getPackageManager().getLaunchIntentForPackage(app);
        ComponentName component = launch == null ? null : launch.getComponent();
        String name = component == null ? null : mapping.components.get(component.flattenToString());
        if (name == null)
            name = mapping.packages.get(app);
        if (name == null)
            return null;
        if (name.startsWith("@calendar:"))
            name = name.substring(10) + LocalDate.now().getDayOfMonth();
        try {
            int id = mapping.resources.getIdentifier(name, "drawable", pack);
            if (id == 0)
                id = mapping.resources.getIdentifier(name, "mipmap", pack);
            return id == 0 ? null : androidx.core.content.res.ResourcesCompat.getDrawable(mapping.resources, id, null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static synchronized Mapping mapping(Context c, String pack) {
        String key = version(c, pack) + ":" + c.getResources().getConfiguration().toString();
        if (key.equals(cachedKey))
            return cached;
        Mapping mapping = null;
        try {
            Context other = c.createPackageContext(pack, 0)
                    .createConfigurationContext(c.getResources().getConfiguration());
            Resources res = other.getResources();
            Map<String, String> entries;
            int id = res.getIdentifier("appfilter", "xml", pack);
            if (id != 0) {
                try (android.content.res.XmlResourceParser parser = res.getXml(id)) {
                    entries = parse(parser);
                }
            } else {
                try (InputStream stream = res.getAssets().open("appfilter.xml")) {
                    XmlPullParser parser = Xml.newPullParser();
                    parser.setInput(stream, "UTF-8");
                    entries = parse(parser);
                }
            }
            mapping = new Mapping(res, entries);
        } catch (Exception ignored) {
            // Missing/uninstalled/malformed packs must never prevent app or widget
            // rendering.
        }
        cachedKey = key;
        cached = mapping;
        return mapping;
    }

    static Map<String, String> parse(XmlPullParser parser) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        int events = 0;
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (++events > 250000)
                throw new IllegalArgumentException("Icon pack mapping is too large");
            if (event != XmlPullParser.START_TAG)
                continue;
            boolean calendar = "calendar".equals(parser.getName());
            if (!calendar && !"item".equals(parser.getName()))
                continue;
            String component = parser.getAttributeValue(null, "component");
            String name = parser.getAttributeValue(null, calendar ? "prefix" : "drawable");
            if (component == null || name == null || name.isEmpty())
                continue;
            component = component.trim();
            if (component.startsWith("ComponentInfo{") && component.endsWith("}"))
                component = component.substring(14, component.length() - 1);
            ComponentName parsed = ComponentName.unflattenFromString(component);
            if (parsed == null)
                continue;
            entries.put(parsed.flattenToString(), calendar ? "@calendar:" + name : name);
        }
        return entries;
    }

    private static final class Mapping {
        final Resources resources;
        final Map<String, String> components, packages = new HashMap<>();
        Mapping(Resources resources, Map<String, String> components) {
            this.resources = resources;
            this.components = components;
            for (Map.Entry<String, String> entry : components.entrySet()) {
                ComponentName component = ComponentName.unflattenFromString(entry.getKey());
                if (component != null)
                    packages.putIfAbsent(component.getPackageName(), entry.getValue());
            }
        }
    }
}
