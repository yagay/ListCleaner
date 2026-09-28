package com.yagay.ListCleaner.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Small hierarchy-aware access layer, independently testable without Android. */
public final class OrderingAccess {
    private OrderingAccess() {}

    public static Object field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    public static Object call(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }

    public static Object targetIntent(Object adapter) throws ReflectiveOperationException {
        try { return call(adapter, "getTargetIntent"); }
        catch (NoSuchMethodException ignored) { }
        try { return field(adapter, "mTargetIntent"); }
        catch (NoSuchFieldException ignored) { }
        return call(field(adapter, "mResolverListCommunicator"), "getTargetIntent");
    }

    public static boolean isChooser(Object adapter) {
        for (Class<?> type = adapter.getClass(); type != null; type = type.getSuperclass()) {
            String name = type.getName();
            if (name.equals("com.android.internal.app.ChooserListAdapter") ||
                    name.equals("com.android.intentresolver.ChooserListAdapter")) return true;
        }
        return false;
    }
}
