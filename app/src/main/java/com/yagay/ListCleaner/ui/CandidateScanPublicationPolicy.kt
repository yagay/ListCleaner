package com.yagay.ListCleaner.ui

/** Reject a late scan or App-Link enrichment result from another refresh/user. */
internal object CandidateScanPublicationPolicy {
    fun isCurrent(
        requestGeneration: Long,
        activeGeneration: Long,
        requestUserId: Int,
        activeUserId: Int,
        displayedUserId: Int?,
    ): Boolean = requestGeneration == activeGeneration &&
        requestUserId == activeUserId && requestUserId == displayedUserId
}
