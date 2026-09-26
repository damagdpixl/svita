package com.damagdpixl.svita.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelContractsTest {
    @Test
    fun schemaVersionIsPositive() {
        assertEquals(1, ModelContracts.SCHEMA_VERSION)
    }
}
