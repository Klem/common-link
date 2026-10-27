package org.commonlink.config

import io.mockk.every
import io.mockk.mockk
import org.springframework.beans.factory.ObjectProvider
import org.web3j.protocol.Web3j
import org.web3j.crypto.Hash
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthCall
import org.web3j.protocol.core.methods.response.EthChainId
import org.web3j.protocol.core.methods.response.EthGetCode
import java.io.IOException

/** Selectors of the registry views read by the startup probe. */
private val SUPPORTS_INTERFACE = Hash.sha3String("supportsInterface(bytes4)").substring(2, 10)
private val READER_VERSION = Hash.sha3String("READER_VERSION()").substring(2, 10)
private val HAS_ROLE = Hash.sha3String("hasRole(bytes32,address)").substring(2, 10)
private val RECORDER_ROLE_HEX = Hash.sha3String("RECORDER_ROLE").substring(2)

/** A 32-byte ABI word holding [value]. */
private fun word(value: Long) = "0x" + value.toString(16).padStart(64, '0')

/**
 * Builds a [Web3j] double answering every call of the startup RPC probe.
 *
 * @param chainIdHex what `eth_chainId` returns (`0x7a69` = 31337).
 * @param code what `eth_getCode` returns for any address (`0x` = no contract).
 * @param unreachableCalls number of first `eth_chainId` calls that fail with an [IOException] (connection refused).
 * @param supportsReader what `supportsInterface(bytes4)` answers (the reader interface of registry 3.x).
 * @param readerVersion what `READER_VERSION()` answers.
 * @param recorderHasRole what `hasRole(RECORDER_ROLE, …)` answers.
 * @param pauserHasRole what `hasRole(<any other role>, …)` answers — i.e. `PAUSER_ROLE`.
 */
internal fun stubWeb3j(
    chainIdHex: String = "0x7a69",
    code: String = "0x6080",
    unreachableCalls: Int = 0,
    supportsReader: Boolean = true,
    readerVersion: Long = 1,
    recorderHasRole: Boolean = true,
    pauserHasRole: Boolean = true,
): Web3j {
    var calls = 0
    val chainRequest = mockk<Request<*, EthChainId>>()
    every { chainRequest.send() } answers {
        if (calls++ < unreachableCalls) throw IOException("Failed to connect to rpc.example.org/secret-token")
        EthChainId().apply { result = chainIdHex }
    }
    val codeRequest = mockk<Request<*, EthGetCode>>()
    every { codeRequest.send() } returns EthGetCode().apply { result = code }
    val callRequests = mutableMapOf<String, Request<*, EthCall>>()
    fun callRequest(data: String): Request<*, EthCall> = callRequests.getOrPut(data) {
        val selector = data.removePrefix("0x").take(8)
        val answer = when (selector) {
            SUPPORTS_INTERFACE -> word(if (supportsReader) 1 else 0)
            READER_VERSION -> word(readerVersion)
            HAS_ROLE -> {
                val role = data.removePrefix("0x").substring(8, 72)
                val granted = if (role.equals(RECORDER_ROLE_HEX, ignoreCase = true)) recorderHasRole else pauserHasRole
                word(if (granted) 1 else 0)
            }
            else -> error("unexpected eth_call selector $selector")
        }
        mockk { every { send() } returns EthCall().apply { result = answer } }
    }
    return mockk {
        every { ethChainId() } returns chainRequest
        every { ethGetCode(any(), any()) } returns codeRequest
        every { ethCall(any<Transaction>(), any()) } answers { callRequest(firstArg<Transaction>().data) }
    }
}

/** @return an [ObjectProvider] exposing [web3j] (null = no Web3j bean, as in mock mode). */
internal fun providerOf(web3j: Web3j?): ObjectProvider<Web3j> = mockk { every { getIfAvailable() } returns web3j }
