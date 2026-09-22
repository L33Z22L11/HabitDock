package dev.habitdock;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

/** On-demand export only. Never reads another app's user data. */
final class ApkShare {
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    static void start(Activity activity, Repository.App app) {
        if (!BUSY.compareAndSet(false, true)) {
            Ui.toast(activity, "正在准备安装包，请稍候");
            return;
        }
        Ui.toast(activity, "正在准备安装包…");
        Context context = activity.getApplicationContext();
        new Thread(() -> {
            try {
                PackageInfo pkg = context.getPackageManager().getPackageInfo(app.pkg, 0);
                ApplicationInfo info = pkg.applicationInfo;
                boolean split = info.splitSourceDirs != null && info.splitSourceDirs.length > 0;
                File folder = new File(context.getCacheDir(), "shared-apks");
                if (!folder.isDirectory() && !folder.mkdirs())
                    throw new IOException("cache unavailable");
                prune(folder);
                String safe = (app.label + "_" + String.valueOf(pkg.versionName)).replaceAll("[^\\p{L}\\p{N}._-]", "_");
                if (safe.length() > 80)
                    safe = safe.substring(0, 80);
                File destination = new File(folder, UUID.randomUUID() + "_" + safe + (split ? ".zip" : ".apk"));
                export(new File(info.sourceDir), info.splitSourceDirs, destination);
                Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".apkfiles", destination,
                        safe + (split ? ".zip" : ".apk"));
                Intent send = new Intent(Intent.ACTION_SEND)
                        .setType(split ? "application/zip" : "application/vnd.android.package-archive")
                        .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TITLE, app.label + " 安装包")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                send.setClipData(ClipData.newRawUri("安装包", uri));
                activity.runOnUiThread(() -> {
                    if (activity.isDestroyed() || activity.isFinishing())
                        return;
                    Runnable launch = () -> {
                        try {
                            activity.startActivity(Intent.createChooser(send, "分享安装包"));
                        } catch (ActivityNotFoundException e) {
                            Ui.toast(activity, "没有可接收安装包的应用");
                        }
                    };
                    if (split)
                        new AlertDialog.Builder(activity).setTitle("分享完整分包安装包")
                                .setMessage("这个应用由多个 APK 组成，已打包成 ZIP。接收方需要支持分包的安装器；不包含账号或应用数据。")
                                .setNegativeButton("取消", null).setPositiveButton("分享", (d, w) -> launch.run()).show();
                    else
                        launch.run();
                });
            } catch (IOException | PackageManager.NameNotFoundException | RuntimeException e) {
                activity.runOnUiThread(() -> {
                    if (!activity.isDestroyed())
                        Ui.toast(activity, "无法导出安装包，请检查剩余空间或应用是否已移除");
                });
            } finally {
                BUSY.set(false);
            }
        }, "habitdock-apk-share").start();
    }

    static void export(File base, String[] splits, File destination) throws IOException {
        try {
            if (splits == null || splits.length == 0) {
                Files.copy(base.toPath(), destination.toPath());
                return;
            }
            try (ZipOutputStream zip = new ZipOutputStream(
                    new BufferedOutputStream(new FileOutputStream(destination)))) {
                // APKs are already compressed. Avoid spending battery recompressing them.
                zip.setLevel(Deflater.NO_COMPRESSION);
                add(zip, base, "base.apk");
                for (int i = 0; i < splits.length; i++)
                    add(zip, new File(splits[i]), "split_" + i + "_" + new File(splits[i]).getName());
                zip.putNextEntry(new ZipEntry("安装说明.txt"));
                zip.write("这是分包 APK 合集，需要支持分包的安装器同时安装全部 APK。只包含当前设备已安装的分包，不包含账号、应用数据或另外下载的资源；其他设备可能不兼容。\n"
                        .getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException | RuntimeException e) {
            destination.delete();
            throw e;
        }
    }

    private static void add(ZipOutputStream zip, File source, String name) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(source.toPath(), zip);
        zip.closeEntry();
    }

    private static void prune(File folder) {
        File[] files = folder.listFiles();
        if (files != null)
            for (File file : files)
                if (file.isFile() && file.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
                    file.delete();
    }
}
