package dev.habitdock;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;
import org.json.*;

/**
 * Public release metadata only. Invoked on demand, never by recommendation
 * work.
 */
final class AppUpdates {
    static final String REPOSITORY = "https://github.com/L33Z22L11/HabitDock";
    static final String RELEASES = REPOSITORY + "/releases";
    private static final String LATEST = "https://api.github.com/repos/L33Z22L11/HabitDock/releases/latest";
    private static final Pattern VERSION = Pattern.compile(
            "v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*))?(?:\\+[0-9A-Za-z.-]+)?");

    static final class Failure extends IOException {
        Failure(String message) {
            super(message);
        }
    }

    static final class Release {
        final String tag;

        Release(String tag) {
            this.tag = tag;
        }

        String url() {
            return RELEASES + "/tag/" + Uri.encode(tag);
        }

        boolean newerThan(String installed) {
            Matcher latest = VERSION.matcher(tag), current = VERSION.matcher(installed);
            if (!latest.matches() || latest.group(4) != null || !current.matches())
                throw new IllegalArgumentException("Unrecognized release version");
            for (int i = 1; i <= 3; i++) {
                int order = new BigInteger(latest.group(i)).compareTo(new BigInteger(current.group(i)));
                if (order != 0)
                    return order > 0;
            }
            return current.group(4) != null;
        }
    }

    static String installedVersion(Context context) {
        try {
            String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            return version == null ? "未知" : version;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "未知";
        }
    }

    static Release parse(String response) throws IOException {
        try {
            JSONObject data = new JSONObject(response);
            String tag = data.getString("tag_name");
            Matcher version = VERSION.matcher(tag);
            if (data.getBoolean("draft") || data.getBoolean("prerelease") || !version.matches()
                    || version.group(4) != null)
                throw new Failure("暂未找到可识别的正式版本");
            return new Release(tag);
        } catch (JSONException error) {
            throw new Failure("无法读取版本信息，请稍后重试");
        }
    }

    static String responseError(int status) {
        if (status == 404)
            return "暂未找到公开发布的正式版本";
        if (status == 403 || status == 429)
            return "GitHub 暂时限制访问，请稍后重试";
        return "GitHub 暂时不可用（" + status + "），请稍后重试";
    }

    static Release fromRedirect(String location) throws IOException {
        String prefix = RELEASES + "/tag/";
        if (location == null || !location.startsWith(prefix))
            throw new Failure("暂未找到可识别的正式版本");
        String tag = Uri.decode(location.substring(prefix.length()));
        Matcher version = VERSION.matcher(tag);
        if (!version.matches() || version.group(4) != null)
            throw new Failure("暂未找到可识别的正式版本");
        return new Release(tag);
    }

    private static Release fetchPublicPage() throws IOException {
        // The official latest-release link redirects to its tag. HEAD reads only
        // that destination, avoiding a page download and the shared API rate limit.
        HttpURLConnection connection = (HttpURLConnection) new URL(RELEASES + "/latest").openConnection();
        connection.setRequestMethod("HEAD");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "HabitDock-UpdateCheck");
        try {
            int status = connection.getResponseCode();
            if (status != 302 && status != 301 && status != 307 && status != 308)
                throw new Failure(responseError(status));
            return fromRedirect(connection.getHeaderField("Location"));
        } finally {
            connection.disconnect();
        }
    }

    static Release fetch() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(LATEST).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        connection.setRequestProperty("User-Agent", "HabitDock-UpdateCheck");
        try {
            int status = connection.getResponseCode();
            if (status == 403 || status == 429) {
                connection.disconnect();
                return fetchPublicPage();
            }
            if (status != HttpURLConnection.HTTP_OK)
                throw new Failure(responseError(status));
            try (InputStream input = connection.getInputStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                long deadline = System.nanoTime() + 8_000_000_000L;
                while ((count = input.read(buffer)) != -1) {
                    if (System.nanoTime() > deadline)
                        throw new SocketTimeoutException();
                    if (output.size() + count > 512 * 1024)
                        throw new Failure("版本信息过大，请打开版本页面查看");
                    output.write(buffer, 0, count);
                }
                return parse(output.toString(StandardCharsets.UTF_8.name()));
            }
        } catch (SocketTimeoutException error) {
            throw new Failure("连接 GitHub 超时，请稍后重试");
        } catch (Failure error) {
            throw error;
        } catch (IOException error) {
            throw new Failure("无法连接 GitHub，请检查网络后重试");
        } finally {
            connection.disconnect();
        }
    }
}
