package nl.nulldevelopment.nfcappblocker.hceemulator.type4

import nl.nulldevelopment.nfcappblocker.tagformat.TagMessage
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

class Type4TagTest {

    private val uuid = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")
    private val message = TagMessage.encode(uuid)
    private var currentMessage = message
    private val tag = Type4Tag { currentMessage }

    private fun apdu(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private val selectNdefApp = apdu(0x00, 0xA4, 0x04, 0x00, 0x07, 0xD2, 0x76, 0x00, 0x00, 0x85, 0x01, 0x01, 0x00)
    private val selectCc = apdu(0x00, 0xA4, 0x00, 0x0C, 0x02, 0xE1, 0x03)
    private val selectNdefFile = apdu(0x00, 0xA4, 0x00, 0x0C, 0x02, 0xE1, 0x04)
    private fun readBinary(offset: Int, length: Int) = apdu(0x00, 0xB0, offset ushr 8, offset and 0xFF, length)

    private fun ByteArray.status() = StatusWord.of(this)
    private fun ByteArray.data() = copyOfRange(0, size - 2)

    /** Performs the read sequence Android's NDEF reader sends, honouring MLe from the CC. */
    private fun readNdefMessage(): ByteArray {
        assertEquals(StatusWord.OK, tag.process(selectNdefApp).status())
        assertEquals(StatusWord.OK, tag.process(selectCc).status())
        val cc = tag.process(readBinary(0, 0x0F)).also { assertEquals(StatusWord.OK, it.status()) }.data()
        val mle = ((cc[3].toInt() and 0xFF) shl 8) or (cc[4].toInt() and 0xFF)

        assertEquals(StatusWord.OK, tag.process(selectNdefFile).status())
        val nlenBytes = tag.process(readBinary(0, 2)).data()
        val nlen = ((nlenBytes[0].toInt() and 0xFF) shl 8) or (nlenBytes[1].toInt() and 0xFF)

        val out = ByteArrayOutputStream()
        var offset = 2
        while (offset < 2 + nlen) {
            val chunk = minOf(mle, 2 + nlen - offset)
            val response = tag.process(readBinary(offset, chunk))
            assertEquals(StatusWord.OK, response.status())
            out.write(response.data())
            offset += chunk
        }
        return out.toByteArray()
    }

    @Nested
    inner class HappyPath {
        @Test
        fun `full reader sequence returns our message`() {
            val read = readNdefMessage()

            assertArrayEquals(message, read)
            assertEquals(TagMessage.Result.Ours(uuid), TagMessage.parse(read))
        }

        @Test
        fun `message is larger than MLe so the read is split into several chunks`() {
            assertTrue(message.size > Type4Tag.MAX_READ_LENGTH)
            assertArrayEquals(message, readNdefMessage())
        }

        @Test
        fun `capability container describes a read-only NDEF file of the right size`() {
            tag.process(selectNdefApp)
            tag.process(selectCc)
            val cc = tag.process(readBinary(0, 0x0F)).data()

            assertArrayEquals(
                apdu(
                    0x00, 0x0F, 0x20, 0x00, 0x3B, 0x00, 0x34,
                    0x04, 0x06, 0xE1, 0x04, 0x00, message.size + 2, 0x00, 0xFF,
                ),
                cc,
            )
        }

        @Test
        fun `select application without Le is accepted`() {
            assertEquals(StatusWord.OK, tag.process(selectNdefApp.copyOf(selectNdefApp.size - 1)).status())
        }

        @Test
        fun `select file with P2 00 is accepted`() {
            tag.process(selectNdefApp)

            assertEquals(StatusWord.OK, tag.process(apdu(0x00, 0xA4, 0x00, 0x00, 0x02, 0xE1, 0x03)).status())
        }

        @Test
        fun `read with Le 00 returns up to 256 bytes`() {
            tag.process(selectNdefApp)
            tag.process(selectNdefFile)

            assertEquals(message.size + 2, tag.process(readBinary(0, 0x00)).data().size)
        }

        @Test
        fun `read near the end of the file is truncated to the file size`() {
            tag.process(selectNdefApp)
            tag.process(selectNdefFile)
            val lastOffset = message.size + 1

            val response = tag.process(readBinary(lastOffset, 0x10))

            assertEquals(StatusWord.OK, response.status())
            assertArrayEquals(byteArrayOf(message.last()), response.data())
        }
    }

    @Nested
    inner class Errors {
        @Test
        fun `selecting a file before the application is file not found`() {
            assertEquals(StatusWord.FILE_NOT_FOUND, tag.process(selectCc).status())
        }

        @Test
        fun `reading before any file is selected is not allowed`() {
            tag.process(selectNdefApp)

            assertEquals(StatusWord.COMMAND_NOT_ALLOWED, tag.process(readBinary(0, 2)).status())
        }

        @Test
        fun `unknown application is file not found`() {
            val selectOther = apdu(0x00, 0xA4, 0x04, 0x00, 0x05, 0xA0, 0x00, 0x00, 0x00, 0x03, 0x00)

            assertEquals(StatusWord.FILE_NOT_FOUND, tag.process(selectOther).status())
        }

        @Test
        fun `selecting another application deselects ours`() {
            tag.process(selectNdefApp)
            tag.process(apdu(0x00, 0xA4, 0x04, 0x00, 0x02, 0xA0, 0x00))

            assertEquals(StatusWord.FILE_NOT_FOUND, tag.process(selectCc).status())
        }

        @Test
        fun `unknown file is file not found`() {
            tag.process(selectNdefApp)

            assertEquals(StatusWord.FILE_NOT_FOUND, tag.process(apdu(0x00, 0xA4, 0x00, 0x0C, 0x02, 0xE1, 0x05)).status())
        }

        @Test
        fun `offset at or beyond the end of the file is rejected`() {
            tag.process(selectNdefApp)
            tag.process(selectNdefFile)

            assertEquals(StatusWord.WRONG_P1_P2, tag.process(readBinary(message.size + 2, 1)).status())
            assertEquals(StatusWord.WRONG_P1_P2, tag.process(readBinary(0x1000, 1)).status())
        }

        @Test
        fun `read binary with short EF addressing is rejected`() {
            tag.process(selectNdefApp)
            tag.process(selectNdefFile)

            assertEquals(StatusWord.INCORRECT_P1_P2, tag.process(apdu(0x00, 0xB0, 0x81, 0x00, 0x02)).status())
        }

        @Test
        fun `read binary without Le is wrong length`() {
            tag.process(selectNdefApp)
            tag.process(selectNdefFile)

            assertEquals(StatusWord.WRONG_LENGTH, tag.process(apdu(0x00, 0xB0, 0x00, 0x00)).status())
        }

        @Test
        fun `update binary is refused because the tag is read-only`() {
            tag.process(selectNdefApp)
            tag.process(selectNdefFile)

            assertEquals(
                StatusWord.SECURITY_STATUS_NOT_SATISFIED,
                tag.process(apdu(0x00, 0xD6, 0x00, 0x00, 0x02, 0x00, 0x00)).status(),
            )
        }

        @Test
        fun `unknown instruction is not supported`() {
            assertEquals(StatusWord.INS_NOT_SUPPORTED, tag.process(apdu(0x00, 0xCA, 0x00, 0x00, 0x00)).status())
        }

        @Test
        fun `non zero class byte is not supported`() {
            assertEquals(StatusWord.CLA_NOT_SUPPORTED, tag.process(apdu(0x90, 0xA4, 0x04, 0x00, 0x00)).status())
        }

        @Test
        fun `invalid select P1 is rejected`() {
            assertEquals(StatusWord.INCORRECT_P1_P2, tag.process(apdu(0x00, 0xA4, 0x02, 0x0C, 0x02, 0xE1, 0x03)).status())
        }

        @Test
        fun `apdus that are too short or inconsistent are wrong length`() {
            assertEquals(StatusWord.WRONG_LENGTH, tag.process(apdu(0x00, 0xA4)).status())
            assertEquals(StatusWord.WRONG_LENGTH, tag.process(apdu(0x00, 0xA4, 0x04, 0x00, 0x07, 0xD2)).status())
            assertEquals(StatusWord.WRONG_LENGTH, tag.process(apdu(0x00, 0xA4, 0x00, 0x0C, 0x00, 0x00, 0x00)).status())
        }

        @Test
        fun `select file with wrong id length is wrong length`() {
            tag.process(selectNdefApp)

            assertEquals(StatusWord.WRONG_LENGTH, tag.process(apdu(0x00, 0xA4, 0x00, 0x0C, 0x01, 0xE1)).status())
        }
    }

    @Nested
    inner class Sessions {
        @Test
        fun `reset forgets the selected application and file`() {
            tag.process(selectNdefApp)
            tag.process(selectNdefFile)

            tag.reset()

            assertEquals(StatusWord.COMMAND_NOT_ALLOWED, tag.process(readBinary(0, 2)).status())
            assertEquals(StatusWord.FILE_NOT_FOUND, tag.process(selectNdefFile).status())
        }

        @Test
        fun `a new session after reset reads normally`() {
            readNdefMessage()
            tag.reset()

            assertArrayEquals(message, readNdefMessage())
        }

        @Test
        fun `message changes mid-session don't affect the current session`() {
            tag.process(selectNdefApp)
            currentMessage = TagMessage.encode(UUID.randomUUID())
            tag.process(selectNdefFile)

            val firstChunk = tag.process(readBinary(2, Type4Tag.MAX_READ_LENGTH)).data()

            assertArrayEquals(message.copyOf(Type4Tag.MAX_READ_LENGTH), firstChunk)
        }

        @Test
        fun `the next session picks up a changed message`() {
            val newUuid = UUID.randomUUID()
            readNdefMessage()
            tag.reset()
            currentMessage = TagMessage.encode(newUuid)

            assertEquals(TagMessage.Result.Ours(newUuid), TagMessage.parse(readNdefMessage()))
        }
    }

    @Nested
    inner class Descriptions {
        @Test
        fun `commands are described for the log`() {
            assertEquals("SELECT NDEF application", describeCommand(selectNdefApp))
            assertEquals("SELECT capability container", describeCommand(selectCc))
            assertEquals("SELECT NDEF file", describeCommand(selectNdefFile))
            assertEquals("READ BINARY offset 2, length 59", describeCommand(readBinary(2, 59)))
        }

        @Test
        fun `responses are described with status and data size`() {
            assertEquals("9000 OK, 2 data bytes", describeResponse(apdu(0x00, 0x5A, 0x90, 0x00)))
            assertEquals("6A82 File or application not found", describeResponse(StatusWord.FILE_NOT_FOUND.bytes))
        }
    }
}
