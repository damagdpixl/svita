package com.damagdpixl.svita.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class EngineContractsTest {
    @Test
    fun algorithmVersionIsPinned() {
        assertEquals("0", EngineContracts.ALGORITHM_VERSION)
    }
}
