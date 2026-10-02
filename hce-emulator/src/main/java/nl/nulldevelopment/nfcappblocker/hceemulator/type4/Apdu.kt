package nl.nulldevelopment.nfcappblocker.hceemulator.type4

/**
 * A parsed short command APDU (ISO 7816-4).
 *
 * [ne] is the expected response length with `Le = 00` already translated to 256, or null when
 * the command has no Le field.
 */
class CommandApdu(
    val cla: Int,
    val ins: Int,
    val p1: Int,
    val p2: Int,
    val data: ByteArray,
    val ne: Int?,
) {
    companion object {
        /** Returns null for anything that isn't a well-formed short APDU, including extended length. */
        fun parse(bytes: ByteArray): CommandApdu? {
            if (bytes.size < 4) return null
            val cla = bytes[0].u8
            val ins = bytes[1].u8
            val p1 = bytes[2].u8
            val p2 = bytes[3].u8
            if (bytes.size == 4) return CommandApdu(cla, ins, p1, p2, ByteArray(0), ne = null)
            if (bytes.size == 5) return CommandApdu(cla, ins, p1, p2, ByteArray(0), ne = bytes[4].u8.orIfZero(256))

            val lc = bytes[4].u8
            if (lc == 0) return null
            val data = bytes.copyOfRange(5, minOf(5 + lc, bytes.size))
            return when (bytes.size) {
                5 + lc -> CommandApdu(cla, ins, p1, p2, data, ne = null)
                6 + lc -> CommandApdu(cla, ins, p1, p2, data, ne = bytes.last().u8.orIfZero(256))
                else -> null
            }
        }

        private fun Int.orIfZero(value: Int) = if (this == 0) value else this
    }
}

object Instruction {
    const val SELECT = 0xA4
    const val READ_BINARY = 0xB0
    const val UPDATE_BINARY = 0xD6
}

/** Status words returned by the emulated tag. */
enum class StatusWord(val sw1: Int, val sw2: Int, val label: String) {
    OK(0x90, 0x00, "OK"),
    WRONG_LENGTH(0x67, 0x00, "Wrong length"),
    SECURITY_STATUS_NOT_SATISFIED(0x69, 0x82, "Security status not satisfied"),
    COMMAND_NOT_ALLOWED(0x69, 0x86, "Command not allowed (no file selected)"),
    FILE_NOT_FOUND(0x6A, 0x82, "File or application not found"),
    INCORRECT_P1_P2(0x6A, 0x86, "Incorrect P1/P2"),
    WRONG_P1_P2(0x6B, 0x00, "Offset outside file"),
    INS_NOT_SUPPORTED(0x6D, 0x00, "Instruction not supported"),
    CLA_NOT_SUPPORTED(0x6E, 0x00, "Class not supported");

    val bytes: ByteArray get() = byteArrayOf(sw1.toByte(), sw2.toByte())
    val hex: String get() = "%02X%02X".format(sw1, sw2)

    companion object {
        fun of(response: ByteArray): StatusWord? {
            if (response.size < 2) return null
            val sw1 = response[response.size - 2].u8
            val sw2 = response[response.size - 1].u8
            return entries.firstOrNull { it.sw1 == sw1 && it.sw2 == sw2 }
        }
    }
}

/** Human-readable summary of a command, for the debug log. */
fun describeCommand(bytes: ByteArray): String {
    val apdu = CommandApdu.parse(bytes) ?: return "Malformed APDU"
    return when (apdu.ins) {
        Instruction.SELECT -> when {
            apdu.p1 == 0x04 && apdu.data.contentEquals(Type4Tag.NDEF_APPLICATION_AID) -> "SELECT NDEF application"
            apdu.p1 == 0x04 -> "SELECT AID ${apdu.data.toHex(separator = "")}"
            apdu.data.contentEquals(Type4Tag.CC_FILE_ID) -> "SELECT capability container"
            apdu.data.contentEquals(Type4Tag.NDEF_FILE_ID) -> "SELECT NDEF file"
            else -> "SELECT file ${apdu.data.toHex(separator = "")}"
        }
        Instruction.READ_BINARY -> "READ BINARY offset ${(apdu.p1 shl 8) or apdu.p2}, length ${apdu.ne ?: 0}"
        Instruction.UPDATE_BINARY -> "UPDATE BINARY"
        else -> "Unknown instruction %02X".format(apdu.ins)
    }
}

/** Human-readable summary of a response, for the debug log. */
fun describeResponse(bytes: ByteArray): String {
    val status = StatusWord.of(bytes)
    val statusText = status?.let { "${it.hex} ${it.label}" } ?: "Unknown status"
    val dataSize = (bytes.size - 2).coerceAtLeast(0)
    return if (dataSize > 0) "$statusText, $dataSize data bytes" else statusText
}

fun ByteArray.toHex(separator: String = " "): String = joinToString(separator) { "%02X".format(it.u8) }

internal val Byte.u8: Int get() = toInt() and 0xFF
