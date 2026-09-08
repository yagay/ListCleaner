package com.yagay.ListCleaner.ui

import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.LocaleText
import com.yagay.ListCleaner.data.ComponentRootCommand
import com.yagay.ListCleaner.data.RootComponent
import com.yagay.ListCleaner.data.RootComponentCatalog
import com.yagay.ListCleaner.data.RootComponentScan
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns Root component scanning/mutation state so MainViewModel can focus on resolver rules and module state. */
internal class RootComponentsController(
    app: ListCleanerApp,
    private val scope: CoroutineScope
) {
    private val catalog = RootComponentCatalog(app)

    private val mutableScan = MutableStateFlow(RootComponentScan())
    val scan: StateFlow<RootComponentScan> = mutableScan

    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy

    private val mutableMessage = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = mutableMessage

    private val mutableRootNotice = MutableStateFlow<String?>(null)
    val rootNotice: StateFlow<String?> = mutableRootNotice

    val lastOperation: String get() = catalog.lastOperation

    fun dismissRootNotice() { mutableRootNotice.value = null }

    private fun localized(message: String?): String? = LocaleText.rootMessage(message)

    private fun localizedScan(scan: RootComponentScan): RootComponentScan =
        scan.copy(warning = localized(scan.warning).orEmpty())

    fun refresh() {
        if (mutableBusy.value) return
        mutableBusy.value = true
        scope.launch {
            try {
                mutableScan.value = withContext(Dispatchers.IO) { localizedScan(catalog.scan()) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableMessage.value = localized(failure.message ?: "扫描失败")
            } finally {
                mutableBusy.value = false
            }
        }
    }

    fun change(target: RootComponent, enable: Boolean) = change(listOf(target), enable)

    fun change(visibleTargets: List<RootComponent>, enable: Boolean) {
        if (mutableBusy.value) return
        val targets = visibleTargets.filter {
            it.blocked == null && it.enabled != null && it.enabled != enable
        }.distinctBy { "${it.user}|${it.component.flattenToString()}" }
        if (targets.isEmpty()) return
        mutableRootNotice.value = null
        mutableBusy.value = true
        mutableMessage.value = localized("正在请求 Root 并核验系统状态…")
        scope.launch {
            withContext(NonCancellable + Dispatchers.IO) {
                var completed = 0
                var operationStarted = false
                try {
                    catalog.requireRoot()
                    var result = ""
                    for (target in targets) {
                        operationStarted = true
                        mutableMessage.value = localized("正在${if (enable) "启用" else "禁用"} ${completed + 1}/${targets.size}：${target.label}")
                        result = catalog.change(target, enable)
                        completed++
                    }
                    mutableMessage.value = localized(if (targets.size == 1) result
                    else "已核验：$completed 个组件已${if (enable) "启用" else "禁用"}；请重新打开目标选择器")
                } catch (failure: ComponentRootCommand.RootAccessException) {
                    mutableMessage.value = localized(failure.message)
                    mutableRootNotice.value = localized(failure.message)
                } catch (failure: Exception) {
                    mutableMessage.value = localized("已完成 $completed/${targets.size}，操作已停止：${failure.message ?: "操作失败"}。失败项请核对系统状态；剩余项未执行，已完成项不回滚。")
                } finally {
                    if (operationStarted) refreshAfterMutation()
                    mutableBusy.value = false
                }
            }
        }
    }

    fun invert(visibleTargets: List<RootComponent>) {
        if (mutableBusy.value) return
        val targets = visibleTargets.filter {
            it.blocked == null && it.enabled != null
        }.distinctBy { "${it.user}|${it.component.flattenToString()}" }
        if (targets.isEmpty()) return
        mutableRootNotice.value = null
        mutableBusy.value = true
        mutableMessage.value = localized("正在请求 Root 并反选组件…")
        scope.launch {
            withContext(NonCancellable + Dispatchers.IO) {
                var completed = 0
                var operationStarted = false
                try {
                    catalog.requireRoot()
                    for (target in targets) {
                        operationStarted = true
                        mutableMessage.value = localized("正在反选 ${completed + 1}/${targets.size}：${target.label}")
                        catalog.change(target, target.enabled == false)
                        completed++
                    }
                    mutableMessage.value = localized("已核验：$completed 个组件已反选；请重新打开目标选择器")
                } catch (failure: ComponentRootCommand.RootAccessException) {
                    mutableMessage.value = localized(failure.message)
                    mutableRootNotice.value = localized(failure.message)
                } catch (failure: Exception) {
                    mutableMessage.value = localized("已完成 $completed/${targets.size}，反选已停止：${failure.message ?: "操作失败"}。已完成项不回滚。")
                } finally {
                    if (operationStarted) refreshAfterMutation()
                    mutableBusy.value = false
                }
            }
        }
    }

    private fun refreshAfterMutation() {
        runCatching { catalog.scan() }
            .onSuccess { mutableScan.value = localizedScan(it) }
            .onFailure { mutableScan.value = RootComponentScan(warning = localized("操作后扫描失败，请刷新；不使用旧状态").orEmpty()) }
    }
}
