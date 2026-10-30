package com.yagay.ListCleaner.runtime;

import android.os.IBinder;
import android.os.ServiceManager;
import android.app.NotificationChannel;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Method;
import java.util.List;

/** Read-only root app_process probe. No notifications are modified or read. */
public final class RootNotificationChannelProbe {
    private RootNotificationChannelProbe() { }

    public static void main(String[] args) {
        try {
            if (args.length != 2) throw new IllegalArgumentException("expected package and uid");
            String pkg = args[0];
            int uid = Integer.parseInt(args[1]);
            if (!pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+") || uid < 0) {
                throw new IllegalArgumentException("invalid target");
            }
            IBinder binder = ServiceManager.getService("notification");
            if (binder == null) throw new IllegalStateException("notification service absent");
            Class<?> stub = Class.forName("android.app.INotificationManager$Stub");
            Object service = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            Method query = service.getClass().getMethod(
                "getNotificationChannelsForPackage", String.class, int.class, boolean.class);
            Object slice = query.invoke(service, pkg, uid, false);
            Object value = slice.getClass().getMethod("getList").invoke(slice);
            JSONArray result = new JSONArray();
            for (Object object : (List<?>) value) {
                NotificationChannel channel = (NotificationChannel) object;
                JSONObject row = new JSONObject();
                row.put("id", channel.getId());
                row.put("name", channel.getName() == null ? channel.getId() :
                    channel.getName().toString());
                row.put("importance", channel.getImportance());
                row.put("group", channel.getGroup() == null ? "" : channel.getGroup());
                result.put(row);
            }
            System.out.println("LC_CHANNELS:" + result.toString());
        } catch (Throwable error) {
            System.err.println("LC_CHANNELS_ERROR:" + error.getClass().getSimpleName());
            System.exit(3);
        }
    }
}