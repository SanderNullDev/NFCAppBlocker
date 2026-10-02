package nl.nulldevelopment.nfcappblocker.hceemulator.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID

class ParseUuidStrictTest {

    @ParameterizedTest
    @ValueSource(strings = ["00112233-4455-6677-8899-aabbccddeeff", "00112233-4455-6677-8899-AABBCCDDEEFF", "  00112233-4455-6677-8899-aabbccddeeff  "])
    fun `accepts canonical uuids in any case, ignoring surrounding whitespace`(text: String) {
        assertEquals(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"), parseUuidStrict(text))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "1-2-3-4-5", "00112233445566778899aabbccddeeff", "00112233-4455-6677-8899-aabbccddeef", "g0112233-4455-6677-8899-aabbccddeeff"])
    fun `rejects anything that isn't the canonical form`(text: String) {
        assertNull(parseUuidStrict(text))
    }
}
