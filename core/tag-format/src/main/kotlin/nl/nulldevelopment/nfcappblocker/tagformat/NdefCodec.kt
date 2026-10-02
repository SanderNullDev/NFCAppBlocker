package nl.nulldevelopment.nfcappblocker.tagformat

import java.io.ByteArrayOutputStream

/** Thrown when bytes can't be decoded as an NDEF message. */
class NdefFormatException(message: String) : Exception(message)

/**
 * Encodes and decodes NDEF messages (NFC Forum NDEF 1.0).
 *
 * Supports short and normal records, with or without an ID. Chunked records are rejected:
 * this app never writes them, and a chunked message can't be one of ours.
 */
object NdefCodec {
    private const val FLAG_MB = 0x80
    private const val FLAG_ME = 0x40
    private const val FLAG_CF = 0x20
    private const val FLAG_SR = 0x10
    private const val FLAG_IL = 0x08
    private const val TNF_MASK = 0x07

    fun encode(records: List<NdefRecord>): ByteArray {
        require(records.isNotEmpty()) { "An NDEF message needs at least one record" }
        val out = ByteArrayOutputStream()
        records.forEachIndexed { index, record ->
            require(record.type.size <= 0xFF) { "Record type is longer than 255 bytes" }
            require(record.id.size <= 0xFF) { "Record ID is longer than 255 bytes" }
            val isShort = record.payload.size <= 0xFF
            var header = record.tnf.code
            if (index == 0) header = header or FLAG_MB
            if (index == records.lastIndex) header = header or FLAG_ME
            if (isShort) header = header or FLAG_SR
            if (record.id.isNotEmpty()) header = header or FLAG_IL

            out.write(header)
            out.write(record.type.size)
            if (isShort) {
                out.write(record.payload.size)
            } else {
                val size = record.payload.size
                out.write(size ushr 24)
                out.write(size ushr 16)
                out.write(size ushr 8)
                out.write(size)
            }
            if (record.id.isNotEmpty()) out.write(record.id.size)
            out.write(record.type)
            out.write(record.id)
            out.write(record.payload)
        }
        return out.toByteArray()
    }

    /** @throws NdefFormatException if [bytes] isn't a single well-formed NDEF message. */
    fun decode(bytes: ByteArray): List<NdefRecord> {
        if (bytes.isEmpty()) throw NdefFormatException("Empty message")
        val reader = Reader(bytes)
        val records = mutableListOf<NdefRecord>()
        while (true) {
            val header = reader.u8()
            val isFirst = records.isEmpty()
            val hasMb = header and FLAG_MB != 0
            if (isFirst != hasMb) throw NdefFormatException("Message begin flag on record ${records.size} is wrong")
            if (header and FLAG_CF != 0) throw NdefFormatException("Chunked records are not supported")

            val typeLength = reader.u8()
            val payloadLength = if (header and FLAG_SR != 0) reader.u8().toLong() else reader.u32()
            val idLength = if (header and FLAG_IL != 0) reader.u8() else 0
            if (payloadLength > reader.remaining) {
                throw NdefFormatException("Payload length $payloadLength exceeds remaining ${reader.remaining} bytes")
            }
            val type = reader.bytes(typeLength)
            val id = reader.bytes(idLength)
            val payload = reader.bytes(payloadLength.toInt())
            records += NdefRecord(NdefRecord.Tnf.fromCode(header and TNF_MASK), type, payload, id)

            if (header and FLAG_ME != 0) break
            if (reader.remaining == 0) throw NdefFormatException("Message end flag missing")
        }
        if (reader.remaining != 0) throw NdefFormatException("${reader.remaining} trailing bytes after message end")
        return records
    }

    private class Reader(private val bytes: ByteArray) {
        private var position = 0
        val remaining: Int get() = bytes.size - position

        fun u8(): Int {
            if (remaining < 1) throw NdefFormatException("Unexpected end of message")
            return bytes[position++].toInt() and 0xFF
        }

        fun u32(): Long = (0 until 4).fold(0L) { acc, _ -> (acc shl 8) or u8().toLong() }

        fun bytes(count: Int): ByteArray {
            if (count > remaining) throw NdefFormatException("Unexpected end of message")
            return bytes.copyOfRange(position, position + count).also { position += count }
        }
    }
}
