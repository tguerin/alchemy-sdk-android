package com.alchemy.sdk.core.proxy.converters

import com.alchemy.sdk.core.model.BlockTag
import com.alchemy.sdk.core.util.HexString
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class BlockTagParamConverterTest {

    @Test
    fun `should convert block tag to string`() = runTest {
        BlockTagParameterConverter.convert(
            BlockTag.Latest
        ) shouldBeEqualTo "latest"
        BlockTagParameterConverter.convert(
            BlockTag.Safe
        ) shouldBeEqualTo "safe"
        BlockTagParameterConverter.convert(
            BlockTag.Pending
        ) shouldBeEqualTo "pending"
        BlockTagParameterConverter.convert(
            BlockTag.Finalized
        ) shouldBeEqualTo "finalized"
        BlockTagParameterConverter.convert(
            BlockTag.BlockTagNumber(HexString.Companion.from("0x04BC"))
        ) shouldBeEqualTo "0x04bc"
    }
}