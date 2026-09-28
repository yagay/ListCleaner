package com.yagay.ListCleaner.xposed

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Binder
import android.os.SystemClock
import android.util.Log
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.RuntimeProtocol
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Small host adapter used only by YSuite's Xposed entry list.
 *
 * The stable ListCleanerModule remains unchanged and continues to own filtering, ordering and
 * visibility. This bridge only accepts UID-authenticated Runtime Probe v2 traffic sent to the
 * YSuite package, commits the same serialized ModuleConfig to RemotePreferences and returns the
 * standard synthetic ACK. The standalone APK does not load this module.
 */
class SuiteRuntimeProbeBridgeModule : XposedModule() {
    private data class Transfer(
        val callerUid: Int,
        val id: String,
        val digest: String,
        val revision: Long,
        val totalChunks: Int,
        val totalChars: Int,
        val startedAt: Long,
        val chunks: Array<String?>,
    )

    private data class ListResult(val values: List<*>, val rebuild: (List<*>) -> Any?)
    private data class ParceledListAccessor(val getList: Method, val constructor: Constructor<*>)

    private val installed = ConcurrentHashMap.newKeySet<String>()
    private val packagesForUidMethods = ConcurrentHashMap<Class<*>, Method?>()
    private val parceledAccessors = ConcurrentHashMap<Class<*>, ParceledListAccessor>()
    private val preferences: SharedPreferences by lazy(LazyThreadSafetyMode.PUBLICATION) {
        getRemotePreferences(RuleRepository.REMOTE_PREFS)
    }

    @Volatile private var transfer: Transfer? = null
    @Volatile private var lastRevision = -1L

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        installHooks(param.classLoader)
    }

    private fun installHooks(loader: ClassLoader) {
        SYSTEM_QUERY_CLASSES.forEach { className ->
            val clazz = runCatching { Class.forName(className, false, loader) }.getOrNull()
                ?: return@forEach
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { it.declaredMethods.asSequence() }
                .filter(::isQueryMethod)
                .distinctBy(Method::toGenericString)
                .forEach { method ->
                    val key = method.toGenericString()
                    if (!installed.add(key)) return@forEach
                    runCatching {
                        method.isAccessible = true
                        hook(method).intercept(hooker())
                    }.onFailure {
                        installed.remove(key)
                        Log.w(TAG, "Unable to install suite probe bridge", it)
                    }
                }
        }
        Log.i(TAG, "Suite runtime probe bridge ready hooks=${installed.size}")
    }

    private fun hooker() = XposedInterface.Hooker { chain ->
        val intent = chain.args.firstOrNull { it is Intent } as? Intent
        if (!isSuiteProbe(intent)) return@Hooker chain.proceed()

        val callerUid = Binder.getCallingUid()
        if (!verifiedSuiteCaller(chain, callerUid)) return@Hooker chain.proceed()

        val original = chain.proceed()
        handleProbe(intent!!, callerUid, original)
    }

    private fun isSuiteProbe(intent: Intent?): Boolean =
        intent != null &&
            intent.selector == null &&
            intent.action == RuntimeProtocol.ACTION &&
            intent.`package` == SUITE_PACKAGE

    private fun verifiedSuiteCaller(chain: XposedInterface.Chain, callerUid: Int): Boolean {
        if (callerUid < 0) return false
        val candidates = buildList {
            chain.thisObject?.let(::add)
            chain.args.filterNotNull().forEach(::add)
        }
        val verified = candidates.any { value ->
            val method = packagesForUidMethod(value.javaClass) ?: return@any false
            SUITE_PACKAGE in packagesForUid(value, method, callerUid)
        }
        if (!verified) Log.w(TAG, "Rejected unverified probe uid=$callerUid")
        return verified
    }

    private fun packagesForUidMethod(clazz: Class<*>): Method? =
        packagesForUidMethods.computeIfAbsent(clazz) {
            generateSequence(clazz as Class<*>?) { it.superclass }
                .flatMap { current -> current.declaredMethods.asSequence() }
                .firstOrNull { method ->
                    method.name == "getPackagesForUid" &&
                        method.parameterTypes.size == 1 &&
                        method.parameterTypes[0] == Int::class.javaPrimitiveType
                }?.apply { isAccessible = true }
        }

    private fun packagesForUid(owner: Any, method: Method, uid: Int): Set<String> {
        val identity = Binder.clearCallingIdentity()
        return try {
            when (val value = method.invoke(owner, uid)) {
                is Array<*> -> value.filterIsInstance<String>().toSet()
                is Collection<*> -> value.filterIsInstance<String>().toSet()
                else -> emptySet()
            }
        } catch (_: Throwable) {
            emptySet()
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    @Synchronized
    private fun handleProbe(intent: Intent, callerUid: Int, original: Any?): Any? {
        if (intent.getIntExtra(RuntimeProtocol.EXTRA_PROTOCOL_VERSION, -1) != RuntimeProtocol.VERSION) {
            return original
        }

        return when (intent.getStringExtra(RuntimeProtocol.EXTRA_OPERATION)) {
            RuntimeProtocol.OP_BEGIN -> {
                begin(intent, callerUid)
                original
            }
            RuntimeProtocol.OP_CHUNK -> {
                append(intent, callerUid)
                original
            }
            RuntimeProtocol.OP_COMMIT -> {
                val digest = commit(intent, callerUid) ?: return original
                ack(original, callerUid, digest, lastRevision)
            }
            null -> {
                val digest = intent.getStringExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST)
                    ?.takeIf(RuntimeProtocol::validDigest)
                    ?: return original
                val stored = preferences.getString(RuleRepository.KEY_CONFIG, null)
                if (stored != null && RuntimeProtocol.digest(stored) == digest) {
                    ack(original, callerUid, digest, lastRevision)
                } else {
                    original
                }
            }
            else -> original
        }
    }

    private fun begin(intent: Intent, callerUid: Int): Boolean {
        val id = intent.getStringExtra(RuntimeProtocol.EXTRA_TRANSFER_ID)
        val digest = intent.getStringExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST)
        val revision = intent.getLongExtra(RuntimeProtocol.EXTRA_REVISION, -1L)
        val chunks = intent.getIntExtra(RuntimeProtocol.EXTRA_TOTAL_CHUNKS, -1)
        val chars = intent.getIntExtra(RuntimeProtocol.EXTRA_TOTAL_CHARS, -1)
        if (!RuntimeProtocol.validTransferId(id) || !RuntimeProtocol.validDigest(digest) ||
            revision < 0L || chunks !in 1..RuntimeProtocol.MAX_CONFIG_CHUNKS ||
            chars !in 1..RuleRepository.MAX_BACKUP_CHARS
        ) {
            transfer = null
            return false
        }
        transfer = Transfer(
            callerUid = callerUid,
            id = requireNotNull(id),
            digest = requireNotNull(digest),
            revision = revision,
            totalChunks = chunks,
            totalChars = chars,
            startedAt = SystemClock.elapsedRealtime(),
            chunks = arrayOfNulls(chunks),
        )
        return true
    }

    private fun append(intent: Intent, callerUid: Int): Boolean {
        val current = transfer ?: return false
        if (current.callerUid != callerUid || expired(current)) {
            transfer = null
            return false
        }
        val id = intent.getStringExtra(RuntimeProtocol.EXTRA_TRANSFER_ID)
        val index = intent.getIntExtra(RuntimeProtocol.EXTRA_CHUNK_INDEX, -1)
        val chunk = intent.getStringExtra(RuntimeProtocol.EXTRA_CONFIG_CHUNK)
        if (id != current.id || index !in current.chunks.indices || chunk == null ||
            chunk.length > RuntimeProtocol.CONFIG_CHUNK_CHARS
        ) return false
        current.chunks[index] = chunk
        return true
    }

    private fun commit(intent: Intent, callerUid: Int): String? {
        val current = transfer ?: return null
        transfer = null
        val id = intent.getStringExtra(RuntimeProtocol.EXTRA_TRANSFER_ID)
        val digest = intent.getStringExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST)
        val revision = intent.getLongExtra(RuntimeProtocol.EXTRA_REVISION, -1L)
        if (current.callerUid != callerUid || expired(current) || id != current.id ||
            digest != current.digest || revision != current.revision || current.chunks.any { it == null }
        ) return null

        val encoded = buildString(current.totalChars) {
            current.chunks.forEach { append(requireNotNull(it)) }
        }
        if (encoded.length != current.totalChars || RuntimeProtocol.digest(encoded) != current.digest) {
            return null
        }
        if (!preferences.edit().putString(RuleRepository.KEY_CONFIG, encoded).commit()) return null
        lastRevision = current.revision
        Log.i(TAG, "Applied YSuite runtime config revision=${current.revision} digest=${current.digest}")
        return current.digest
    }

    private fun expired(value: Transfer): Boolean =
        SystemClock.elapsedRealtime() - value.startedAt > TRANSFER_TIMEOUT_MS

    private fun ack(original: Any?, callerUid: Int, digest: String, revision: Long): Any? {
        val result = extractListResult(original) ?: return original
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = SUITE_PACKAGE
                name = RuntimeProtocol.COMPONENT
                applicationInfo = ApplicationInfo().apply {
                    packageName = SUITE_PACKAGE
                    uid = callerUid
                }
            }
            nonLocalizedLabel =
                "${BuildConfig.HOOK_COMPAT_VERSION_CODE}:$digest:0:0:0:$COMPONENT_DISCOVERY_PROTOCOL:" +
                    "${RuntimeProtocol.VERSION}:$revision"
        }
        return result.rebuild(listOf(info))
    }

    private fun extractListResult(original: Any?): ListResult? = when {
        original is List<*> -> ListResult(original) { it }
        original == null -> null
        original.javaClass.name.endsWith("ParceledListSlice") -> {
            val accessor = parceledAccessors.computeIfAbsent(original.javaClass) { clazz ->
                val getList = clazz.getMethod("getList").apply { isAccessible = true }
                val constructor = clazz.getDeclaredConstructor(List::class.java).apply { isAccessible = true }
                ParceledListAccessor(getList, constructor)
            }
            val values = runCatching { accessor.getList.invoke(original) as? List<*> }.getOrNull()
                ?: return null
            ListResult(values) { accessor.constructor.newInstance(it) }
        }
        else -> null
    }

    private fun isQueryMethod(method: Method): Boolean =
        method.name in setOf("queryIntentActivities", "queryIntentActivitiesAsUser", "queryIntentActivitiesInternal") &&
            method.parameterTypes.any { Intent::class.java.isAssignableFrom(it) } &&
            (List::class.java.isAssignableFrom(method.returnType) ||
                method.returnType.name == "android.content.pm.ParceledListSlice")

    private companion object {
        const val TAG = "ListCleaner.SuiteBridge"
        const val SUITE_PACKAGE = "com.yagay.YSuite"
        const val COMPONENT_DISCOVERY_PROTOCOL = 2
        const val TRANSFER_TIMEOUT_MS = 10_000L
        val SYSTEM_QUERY_CLASSES = listOf(
            "com.android.server.pm.PackageManagerService\$IPackageManagerImpl",
            "com.android.server.pm.IPackageManagerImpl",
            "com.android.server.pm.PackageManagerService",
            "com.android.server.pm.ComputerEngine",
            "com.android.server.pm.ComputerLocked",
        )
    }
}
