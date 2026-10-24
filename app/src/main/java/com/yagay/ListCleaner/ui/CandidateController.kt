package com.yagay.ListCleaner.ui

import android.util.Log
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.BrowserLinkDiscovery
import com.yagay.ListCleaner.data.ObservedEntryCache
import com.yagay.ListCleaner.data.ProactiveScanOptions
import com.yagay.ListCleaner.data.ProactiveTrigger
import com.yagay.ListCleaner.domain.AuthorityCandidatePolicy
import com.yagay.ListCleaner.domain.ComponentCandidate
import com.yagay.ListCleaner.domain.ComponentRule
import com.yagay.ListCleaner.domain.normalizeLogicalCandidates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Owns fast catalog scans and slower App-Link enrichment independently from UI navigation state. */
internal class CandidateController(
    private val app: ListCleanerApp,
    private val scope: CoroutineScope,
) {
    private val browserLinkDiscovery = BrowserLinkDiscovery()
    private val observedEntryCache = ObservedEntryCache(app)
    private val mutableCandidates = MutableStateFlow<List<ComponentCandidate>>(emptyList())
    val candidates: StateFlow<List<ComponentCandidate>> = mutableCandidates.asStateFlow()

    private val mutableBrowserHosts = MutableStateFlow<Set<String>>(emptySet())
    val browserHosts: StateFlow<Set<String>> = mutableBrowserHosts.asStateFlow()

    private val mutableLoading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = mutableLoading.asStateFlow()

    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    private var refreshJob: Job? = null
    private var browserRefreshJob: Job? = null
    private var generation = 0L
    private var renderedUserId: Int? = null

    init {
        scope.launch {
            var firstRevision = true
            app.catalog.revision.collectLatest {
                if (firstRevision) {
                    firstRevision = false
                    return@collectLatest
                }
                delay(250)
                refresh()
            }
        }
    }

    fun showError(message: String?) {
        mutableError.value = message
    }

    fun clearForSpaceChange() {
        generation++
        refreshJob?.cancel()
        browserRefreshJob?.cancel()
        mutableCandidates.value = emptyList()
        mutableBrowserHosts.value = emptySet()
        renderedUserId = null
    }

    fun refresh(forceCatalog: Boolean = false, manualProactive: Boolean = false) {
        val userId = app.rules.activeUser.value
        val currentGeneration = ++generation
        // Keep the last known candidates visible during same-profile rescans, and
        // retain them if a transient scan fails. Never carry them to another profile.
        if (renderedUserId != userId) {
            mutableCandidates.value = emptyList()
            mutableBrowserHosts.value = emptySet()
            renderedUserId = userId
        }
        refreshJob?.cancel()
        browserRefreshJob?.cancel()
        refreshJob = scope.launch {
            mutableLoading.value = true
            mutableError.value = null
            try {
                // Pull runtime authority observations before catalog discovery. When the observed
                // cache changes, invalidate the catalog so an old empty special-entry result cannot
                // mask newly observed Role/Settings/NFC/manager candidates.
                val observedBefore = observedEntryCache.revisionToken()
                app.synchronizeObservedEntries(userId)
                if (observedEntryCache.revisionToken() != observedBefore) {
                    app.catalog.invalidate()
                }
                if (currentGeneration != generation || app.rules.activeUser.value != userId) return@launch

                val configured = configuredRules()
                mutableCandidates.value = completeLogical(mutableCandidates.value, configured)

                val cachedBrowserDiscovery = if (userId == app.spaces.currentUserId) browserLinkDiscovery.snapshot()
                    else com.yagay.ListCleaner.data.BrowserLinkDiscoveryResult()
                if (currentGeneration == generation) {
                    mutableBrowserHosts.value = cachedBrowserDiscovery.hosts
                }
                val proactive = UiPreferencesRuntime.current.let { prefs ->
                    ProactiveScanOptions(
                        trigger = when (prefs.proactiveScanMode) {
                            UiProactiveScanMode.ON_SCAN -> ProactiveTrigger.ON_SCAN
                            UiProactiveScanMode.MANUAL_ONLY -> ProactiveTrigger.MANUAL_ONLY
                            UiProactiveScanMode.OFF -> ProactiveTrigger.OFF
                        },
                        includeDirectShare = prefs.proactiveDirectShare,
                        includeShortcutItems = prefs.proactiveShortcutItems,
                        cacheMinutes = prefs.proactiveCacheMinutes,
                    )
                }
                val baseResult = app.catalog.scan(
                    app.rules.openTypes.value.customDefinitions,
                    app.rules.browserLinks.value.hosts + cachedBrowserDiscovery.hosts,
                    browserDiscovery = cachedBrowserDiscovery,
                    force = forceCatalog,
                    userId = userId,
                    proactiveOptions = proactive,
                    manualProactive = manualProactive,
                )
                if (currentGeneration != generation || app.rules.activeUser.value != userId) return@launch
                mutableCandidates.value = completeLogical(baseResult, configuredRules())
                mutableError.value = app.catalog.scanWarning
                mutableLoading.value = false

                // User-requested scans still perform deep link enrichment even when auto discovery is disabled.
                if (userId != app.spaces.currentUserId) return@launch
                if (!UiPreferencesRuntime.current.appLinkEnrichment && !forceCatalog) return@launch
                browserRefreshJob = scope.launch {
                    try {
                        val browserDiscovery = browserLinkDiscovery.discoverDetailed(forceCatalog)
                        if (currentGeneration != generation || app.rules.activeUser.value != userId) return@launch
                        mutableBrowserHosts.value = browserDiscovery.hosts
                        if (forceCatalog || browserDiscovery != cachedBrowserDiscovery) {
                            val enriched = app.catalog.scan(
                                app.rules.openTypes.value.customDefinitions,
                                app.rules.browserLinks.value.hosts + browserDiscovery.hosts,
                                browserDiscovery = browserDiscovery,
                                force = true,
                                userId = userId,
                                proactiveOptions = proactive,
                                manualProactive = false,
                            )
                            if (currentGeneration == generation) {
                                mutableCandidates.value = completeLogical(enriched, configuredRules())
                                mutableError.value = app.catalog.scanWarning
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        Log.w(TAG, "Background App Link enrichment failed", failure)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Candidate scan failed", failure)
                if (currentGeneration == generation) mutableError.value = app.getString(R.string.scan_failed)
            } finally {
                if (currentGeneration == generation) mutableLoading.value = false
            }
        }
    }

    private suspend fun completeLogical(
        items: List<ComponentCandidate>,
        configured: Set<ComponentRule>,
    ): List<ComponentCandidate> = app.catalog.completeConfigured(
        normalizeLogicalCandidates(AuthorityCandidatePolicy.normalize(items)),
        configured,
        userId = app.rules.activeUser.value,
    )

    private fun configuredRules(): Set<ComponentRule> = buildSet {
        addAll(app.rules.rules.value)
        app.rules.openTypes.value.rules.values.flatten().mapNotNullTo(this, ComponentRule::fromId)
        app.rules.browserLinks.value.rules.values.flatten().mapNotNullTo(this, ComponentRule::fromId)
    }

    private companion object {
        const val TAG = "ListCleaner.Candidates"
    }
}

