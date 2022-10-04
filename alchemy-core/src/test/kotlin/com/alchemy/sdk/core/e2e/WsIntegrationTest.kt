package com.alchemy.sdk.core.e2e

import com.alchemy.sdk.core.Alchemy
import com.alchemy.sdk.core.model.AlchemySettings
import com.alchemy.sdk.core.model.core.Network
import com.alchemy.sdk.core.ws.model.WebsocketMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldNotBeEqualTo
import org.junit.Test

class WsIntegrationTest {

    private val alchemy = Alchemy.with(AlchemySettings(network = Network.ETH_MAINNET))

    @Test
    fun `should listen to websocket block method`() = runTest {
        val result = alchemy.ws.on(WebsocketMethod.Block).first()
        result shouldNotBeEqualTo alchemy.core.getBlockNumber().getOrThrow()
    }
}