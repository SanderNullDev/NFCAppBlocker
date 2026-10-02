package nl.nulldevelopment.nfcappblocker.hceemulator.type4

/**
 * Read-only NFC Forum Type 4 Tag (mapping version 2.0) serving one NDEF message.
 *
 * Pure Kotlin with no Android dependencies, so it can be unit tested on the JVM. Not thread
 * safe; the HCE service calls it from a single thread.
 *
 * @param ndefMessage supplies the NDEF message. It's read once per session, when the reader
 *   selects the NDEF application, so changing presets mid-read can't mix two messages.
 */
class Type4Tag(private val ndefMessage: () -> ByteArray) {

    private var applicationSelected = false
    private var capabilityContainer: ByteArray? = null
    private var ndefFile: ByteArray? = null
    private var selectedFile: ByteArray? = null

    fun process(command: ByteArray): ByteArray {
        val apdu = CommandApdu.parse(command) ?: return StatusWord.WRONG_LENGTH.bytes
        if (apdu.cla != 0x00) return StatusWord.CLA_NOT_SUPPORTED.bytes
        return when (apdu.ins) {
            Instruction.SELECT -> select(apdu)
            Instruction.READ_BINARY -> readBinary(apdu)
            Instruction.UPDATE_BINARY -> StatusWord.SECURITY_STATUS_NOT_SATISFIED.bytes
            else -> StatusWord.INS_NOT_SUPPORTED.bytes
        }
    }

    /** Forgets the selected application and file; call when the reader goes away. */
    fun reset() {
        applicationSelected = false
        capabilityContainer = null
        ndefFile = null
        selectedFile = null
    }

    private fun select(apdu: CommandApdu): ByteArray = when (apdu.p1) {
        P1_SELECT_BY_NAME -> selectApplication(apdu)
        P1_SELECT_BY_FILE_ID -> selectFile(apdu)
        else -> StatusWord.INCORRECT_P1_P2.bytes
    }

    private fun selectApplication(apdu: CommandApdu): ByteArray {
        if (apdu.p2 != 0x00) return StatusWord.INCORRECT_P1_P2.bytes
        if (!apdu.data.contentEquals(NDEF_APPLICATION_AID)) {
            reset()
            return StatusWord.FILE_NOT_FOUND.bytes
        }
        val message = ndefMessage()
        val file = ByteArray(NLEN_SIZE + message.size)
        file[0] = (message.size ushr 8).toByte()
        file[1] = message.size.toByte()
        message.copyInto(file, destinationOffset = NLEN_SIZE)

        applicationSelected = true
        ndefFile = file
        capabilityContainer = capabilityContainer(maxNdefFileSize = file.size)
        selectedFile = null
        return StatusWord.OK.bytes
    }

    private fun selectFile(apdu: CommandApdu): ByteArray {
        if (apdu.p2 != P2_NO_RESPONSE_DATA && apdu.p2 != 0x00) return StatusWord.INCORRECT_P1_P2.bytes
        if (apdu.data.size != 2) return StatusWord.WRONG_LENGTH.bytes
        if (!applicationSelected) return StatusWord.FILE_NOT_FOUND.bytes
        selectedFile = when {
            apdu.data.contentEquals(CC_FILE_ID) -> capabilityContainer
            apdu.data.contentEquals(NDEF_FILE_ID) -> ndefFile
            else -> return StatusWord.FILE_NOT_FOUND.bytes
        }
        return StatusWord.OK.bytes
    }

    private fun readBinary(apdu: CommandApdu): ByteArray {
        val file = selectedFile ?: return StatusWord.COMMAND_NOT_ALLOWED.bytes
        // Bit 8 of P1 set means "short EF identifier" addressing, which Type 4 tags don't use.
        if (apdu.p1 and 0x80 != 0) return StatusWord.INCORRECT_P1_P2.bytes
        if (apdu.data.isNotEmpty()) return StatusWord.WRONG_LENGTH.bytes
        val length = apdu.ne ?: return StatusWord.WRONG_LENGTH.bytes
        val offset = (apdu.p1 shl 8) or apdu.p2
        if (offset >= file.size) return StatusWord.WRONG_P1_P2.bytes
        val end = minOf(offset + length, file.size)
        return file.copyOfRange(offset, end) + StatusWord.OK.bytes
    }

    private fun capabilityContainer(maxNdefFileSize: Int): ByteArray = byteArrayOf(
        0x00, 0x0F, // CCLEN: 15 bytes
        0x20, // Mapping version 2.0
        0x00, MAX_READ_LENGTH.toByte(), // MLe
        0x00, MAX_WRITE_LENGTH.toByte(), // MLc
        0x04, 0x06, // NDEF File Control TLV, 6 bytes
        NDEF_FILE_ID[0], NDEF_FILE_ID[1],
        (maxNdefFileSize ushr 8).toByte(), maxNdefFileSize.toByte(),
        0x00, // Read access: granted
        0xFF.toByte(), // Write access: none (read-only tag)
    )

    companion object {
        val NDEF_APPLICATION_AID = byteArrayOf(0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01)
        val CC_FILE_ID = byteArrayOf(0xE1.toByte(), 0x03)
        val NDEF_FILE_ID = byteArrayOf(0xE1.toByte(), 0x04)

        /**
         * Maximum bytes per READ BINARY the reader may request. Deliberately small, so reading
         * our ~90-byte message takes more than one READ BINARY and exercises offset handling.
         */
        const val MAX_READ_LENGTH = 0x3B
        private const val MAX_WRITE_LENGTH = 0x34
        private const val NLEN_SIZE = 2
        private const val P1_SELECT_BY_NAME = 0x04
        private const val P1_SELECT_BY_FILE_ID = 0x00
        private const val P2_NO_RESPONSE_DATA = 0x0C
    }
}
