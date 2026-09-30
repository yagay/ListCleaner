package com.yagay.ListCleaner.ui

import android.util.Log
import com.yagay.ListCleaner.ListCleanerApp
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.data.BrowserLinkDiscovery
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Owns fast catalog scans and slower App-Link enrichment independently from UI navigation state. */
internal class CandidateController(
    private val app: ListCleanerApp,
    private val scope: CoroutineScope,
) {
    private val browserLinkDiscovery = BrowserLinkDiscovery()
    private val mutableCandidates = MutableStateFlow<List<ComponentCandidate>>(emptyList())
    val candidates: StateFlow<List<ComponentCandidate>> = mutableCandidates

    private val mutableBrowserHosts = MutableStateFlow<Set<String>>(emptySet())
    val browserHosts: StateFlow<Set<String>> = mutableBrowserHosts

    private val mutableLoading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = mutableLoading

    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError

    private var refreshJob: Job? = null
    private var browserRefreshJob: Job? = null
    private var generation = 0L

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

    fun refresh(forceCatalog: Boolean = false) {
        val currentGeneration = ++generation
        refreshJob?.cancel()
        browserRefreshJob?.cancel()
        refreshJob = scope.launch {
            mutableLoading.value = true
            mutableError.value = null
            try {
                // Pull chooser-observed Direct Share entries before catalog discovery. This is a
                // lightweight remote preference sync and keeps the list current without rebooting.
                app.synchronizeObservedEntries()
                if (currentGeneration != generation) return@launch

                val configured = configuredRules()
                mutableCandidates.value = completeLogical(mutableCandidates.value, configured)

                val cachedBrowserDiscovery = browserLinkDiscovery.snapshot()
                if (currentGeneration == generation) {
                    mutableBrowserHosts.value = cachedBrowserDiscovery.hosts
                }
                val baseResult = app.catalog.scan(
                    app.rules.openTypes.value.customDefinitions,
                    app.rules.browserLinks.value.hosts + cachedBrowserDiscovery.hosts,
                    browserDiscovery = cachedBrowserDiscovery,
                    force = forceCatalog,
                )
                if (currentGeneration != generation) return@launch
                mutableCandidates.value = completeLogical(baseResult, configuredRules())
                mutableError.value = app.catalog.scanWarning
                mutableLoading.value = false

                browserRefreshJob = scope.launch {
                    try {
                        val browserDiscovery = browserLinkDiscovery.discoverDetailed(forceCatalog)
                        if (currentGeneration != generation) return@launch
                        mutableBrowserHosts.value = browserDiscovery.hosts
                        if (browserDiscovery != cachedBrowserDiscovery) {
                            val enriched = app.catalog.scan(
                                app.rules.openTypes.value.customDefinitions,
                                app.rules.browserLinks.value.hosts + browserDiscovery.hosts,
                                browserDiscovery = browserDiscovery,
                                force = true,
                            )
                            if (currentGeneration == generation) {
                                mutableCandidates.value = completeLogical(enriched, configuredRules())
                                mutableError.value = app.catalog.scanWarning
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        Log.w(TAG, "Background App Link enrichment failed", failure)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
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
