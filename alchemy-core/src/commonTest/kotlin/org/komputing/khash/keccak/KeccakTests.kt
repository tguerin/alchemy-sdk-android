package org.komputing.khash.keccak

import com.alchemy.sdk.util.HexString.Companion.hexString
import io.ktor.utils.io.core.toByteArray
import org.komputing.khash.keccak.extensions.digestKeccak
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the [Keccak.digest] method against the NIST test vectors.
 * The original test vectors can be found here: https://csrc.nist.gov/CSRC/media/Projects/Cryptographic-Algorithm-Validation-Program/documents/sha3/sha-3bittestvectors.zip
 */
class KeccakTests {

    private val allNistTestVectors = listOf(
        testVectors224 to KeccakParameter.SHA3_224,
        testVectors256 to KeccakParameter.SHA3_256,
        testVectors384 to KeccakParameter.SHA3_384,
        testVectors512 to KeccakParameter.SHA3_512
    )

    @Test
    fun testNistVectors() {
        allNistTestVectors.forEach { (vectorList, params) ->
            vectorList.forEach { (input, expected) ->
                assertEquals(
                    Keccak.digest(if (input == "") input.toByteArray() else input.hexString.toByteArray(), params).hexString.withoutPrefix(),
                    expected
                )
            }
        }
    }

    @Test
    fun keccak256hashesAreCorrect() {

        assertTrue(
            "".digestKeccak(KeccakParameter.KECCAK_256)
                .contentEquals("c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470".hexString.toByteArray())
        )

        assertTrue(
            "The quick brown fox jumps over the lazy dog".digestKeccak(KeccakParameter.KECCAK_256)
                .contentEquals("4d741b6f1eb29cb2a9b9911c82f56fa8d73b04959d3d9d222895df6c0b28aa15".hexString.toByteArray())
        )

        assertTrue(
            "The quick brown fox jumps over the lazy dog.".digestKeccak(KeccakParameter.KECCAK_256)
                .contentEquals("578951e24efd62a3d63a86f7cd19aaa53c898fe287d2552133220370240b572d".hexString.toByteArray())
        )

        assertTrue(
            "The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog.The quick brown fox jumps over the lazy dog."
                .digestKeccak(KeccakParameter.KECCAK_256)
                .contentEquals("e35949d2ca446ea2fd99f49bed23c60e0b9849f5384661bc574a5c55fcaeb4bd".hexString.toByteArray())
        )

        assertEquals(
            Keccak.digest(
               "a1b31be4d58a7ddd24b135db0da56a90fb5382077ae26b250e1dc9cd6232ce2270f4c995428bc76aa78e522316e95d7834d725efc9ca754d043233af6ca90113".hexString.toByteArray(),
                KeccakParameter.KECCAK_256
            ).hexString.withoutPrefix(),
            "e1674295e8fc8ffdfb46cadb01c52b08330e05d731e38c856c1043288f7d9744"
        )
    }
}
