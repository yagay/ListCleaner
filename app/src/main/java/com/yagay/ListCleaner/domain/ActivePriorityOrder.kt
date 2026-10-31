package com.yagay.ListCleaner.domain

/** Ranks only candidates in the current full scan, preserving stored IDs. */
fun activePriorityRanks(saved: List<String>, available: Set<String>): Map<String, Int> {
    val ranks = linkedMapOf<String, Int>()
    for (id in saved) if (id in available && id !in ranks) ranks[id] = ranks.size + 1
    return ranks
}

/** Insert after last available priority, leaving old missing entries intact. */
fun addActivePriority(saved: List<String>, available: Set<String>, id: String, limit: Int = 2000): List<String> {
    if (id !in available || id in saved || saved.size >= limit) return saved
    val index = saved.indexOfLast { it in available }
    return saved.take(index + 1) + id + saved.drop(index + 1)
}
