package com.yagay.ListCleaner.xposed

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.util.Log
import com.yagay.ListCleaner.domain.AndroidUid
import com.yagay.ListCleaner.domain.CapturedSettingsPage
import com.yagay.ListCleaner.domain.SettingsPageCaptureProtocol
import com.yagay.ListCleaner.domain.RuntimeCaptureContract
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import kotlinx.serialization.json.Json
import java.lang.reflect.Method
import java.util.concurrent.Executors

/**
 * Detects the real Activity/Fragment the user opens in Settings or PermissionController.
 * No screen reading, extras dump, View traversal or capture unless the manager has armed
 * a short-lived session. The manager's UID-verified provider enforces that gate.
 */
class SettingsPageObserverModule : XposedModule() {
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "ListCleaner-PageObserver").apply { isDaemon = true }
    }
    private val installed = hashSetOf<String>()
    @Volatile private var lastIdentity = ""
    @Volatile private var lastAt = 0L

    override fun onModuleLoaded(param: ModuleLoadedParam) = Unit
    override fun onHotReloading(param: HotReloadingParam): Boolean = false

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName !in SettingsPageCaptureProtocol.HOST_PACKAGES) return
        install("android.app.Activity", param.classLoader)
        install("android.app.Fragment", param.classLoader)
        install("androidx.fragment.app.Fragment", param.classLoader)
    }

    private fun install(className: String, loader: ClassLoader) {
        val clazz = runCatching { Class.forName(className, false, loader) }.getOrNull() ?: return
        val method: Method = clazz.declaredMethods.firstOrNull {
            it.name == "onResume" && it.parameterCount == 0
        } ?: return
        if (!installed.add(method.toGenericString())) return
        runCatching {
            hook(method).setId("lc-settings-page-" + className).intercept(XposedInterface.Hooker { chain ->
                val original = chain.proceed()
                runCatching {
                    val instance = chain.thisObject ?: return@runCatching
                    val activity = if (instance is Activity) instance else {
                        (clazz.getMethod("getActivity").invoke(instance) as? Activity)
                            ?: return@runCatching
                    }
                    val fragment = if (instance is Activity) "" else instance.javaClass.name
                    capture(activity, fragment)
                    if (instance is Activity) SettingsPageNativeControl.maybeAttach(activity, worker)
                }.onFailure {
                    Log.d("ListCleaner.PageObserver", "Capture skipped: " + it.javaClass.simpleName)
                }
                original
            })
        }.onFailure { installed.remove(method.toGenericString()) }
    }

    private fun capture(activity: Activity, fragment: String) {
        val component = activity.componentName ?: return
        if (component.packageName !in SettingsPageCaptureProtocol.HOST_PACKAGES) return
        val intent = activity.intent
        val fragmentName = fragment.ifEmpty {
            intent?.getStringExtra(":settings:show_fragment").orEmpty()
        }
        val page = CapturedSettingsPage(
            packageName = component.packageName,
            activityClass = component.className,
            fragmentClass = fragmentName,
            action = intent?.action?.takeIf { it.startsWith("android.") }.orEmpty(),
            roleName = intent?.getStringExtra("android.intent.extra.ROLE_NAME")
                ?.takeIf { it.startsWith("android.app.role.") }.orEmpty(),
            userId = AndroidUid.userId(Process.myUid()),
            capturedAtMillis = System.currentTimeMillis(),
        )
        if (!SettingsPageCaptureProtocol.valid(page)) return
        val key = page.packageName + "/" + page.activityClass + "/" + page.fragmentClass + "/" +
            page.action + "/" + page.roleName
        val now = android.os.SystemClock.elapsedRealtime()
        if (key == lastIdentity && now - lastAt in 0L..800L) return
        lastIdentity = key
        lastAt = now

        val payload = Json.encodeToString(CapturedSettingsPage.serializer(), page)
        val appContext: Context = activity.applicationContext ?: return
        worker.execute {
            runCatching {
                val arguments = Bundle().apply {
                    putString(SettingsPageCaptureProtocol.EXTRA_PAGE, payload)
                }
                val result = appContext.contentResolver.call(
                    Uri.parse("content://" + RuntimeCaptureContract.AUTHORITY),
                    SettingsPageCaptureProtocol.METHOD_PUBLISH, null, arguments
                )
                if (result?.getBoolean("accepted", false) == true && fragment.isNotEmpty()) {
                    SettingsPageNativeControl.maybeAttach(activity, worker, saved = true)
                }
            }.onFailure {
                Log.d("ListCleaner.PageObserver", "Publish skipped: " + it.javaClass.simpleName)
            }
        }
    }
}
