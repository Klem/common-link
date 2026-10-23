package org.commonlink.config

import io.mockk.every
import io.mockk.mockk
import org.springframework.beans.factory.ObjectProvider
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.EthChainId
import org.web3j.protocol.core.methods.response.EthGetCode
import java.io.IOException

/**
 * Builds a [Web3j] double answering the two calls of the startup RPC probe.
 *
 * @param chainIdHex what `eth_chainId` returns (`0x7a69` = 31337).
 * @param code what `eth_getCode` returns for any address (`0x` = no contract).
 * @param unreachableCalls number of first `eth_chainId` calls that fail with an [IOException] (connection refused).
 */
internal fun stubWeb3j(chainIdHex: String = "0x7a69", code: String = "0x6080", unreachableCalls: Int = 0): Web3j {
    var calls = 0
    val chainRequest = mockk<Request<*, EthChainId>>()
    every { chainRequest.send() } answers {
        if (calls++ < unreachableCalls) throw IOException("Failed to connect to rpc.example.org/secret-token")
        EthChainId().apply { result = chainIdHex }
    }
    val codeRequest = mockk<Request<*, EthGetCode>>()
    every { codeRequest.send() } returns EthGetCode().apply { result = code }
    return mockk {
        every { ethChainId() } returns chainRequest
        every { ethGetCode(any(), any()) } returns codeRequest
    }
}

/** @return an [ObjectProvider] exposing [web3j] (null = no Web3j bean, as in mock mode). */
internal fun providerOf(web3j: Web3j?): ObjectProvider<Web3j> = mockk { every { getIfAvailable() } returns web3j }
