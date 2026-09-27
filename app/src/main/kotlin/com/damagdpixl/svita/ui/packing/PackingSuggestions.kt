package com.damagdpixl.svita.ui.packing

import com.damagdpixl.svita.core.model.WearLogEntry
import kotlinx.datetime.LocalDate

/**
 * Packing suggestion math (P2 T7).
 *
 * The suggestion is the UNION of item ids from every wear_log entry whose date
 * falls in `[from, to]` inclusive — deduplicated, first-seen order preserved.
 *
 * Deliberate asymmetry with the statistics layer (document the contract once,
 * here): statistics in :core:data exclude PLANNED entries (`note = 'plan'`)
 * because a plan is not a wear — while packing INCLUDES plans. Items of an
 * outfit planned for the trip window must land in the suitcase, so both real
 * wears and plans inside the range feed the suggestion. The plan marker stays
 * the shared [com.damagdpixl.svita.core.data.WearLogRepository.PLAN_NOTE]
 * constant; the union itself does not need to read it — plan and wear entries
 * are treated identically here, on purpose.
 */
fun suggestedItemIds(entries: List<WearLogEntry>, from: LocalDate, to: LocalDate): List<Long> =
    entries.asSequence()
        .filter { it.date >= from && it.date <= to }
        .flatMap { it.itemIds.asSequence() }
        .distinct()
        .toList()
