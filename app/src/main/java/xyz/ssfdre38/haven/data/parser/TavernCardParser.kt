package xyz.ssfdre38.haven.data.parser

import android.util.Base64
import xyz.ssfdre38.haven.data.model.TavernCharacterData
import xyz.ssfdre38.haven.data.model.TavernCharacterRoot
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.Inflater
import java.io.ByteArrayOutputStream

object TavernCardParser {

    private val jsonConfig = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * Parses a Tavern character card PNG or raw JSON file into TavernCharacterData.
     */
    fun parse(inputStream: InputStream): TavernCharacterData? {
        val bis = BufferedInputStream(inputStream)
        bis.mark(1024 * 1024 * 5) // Mark up to 5MB for reset if it's raw JSON

        val signature = ByteArray(8)
        val readLen = bis.read(signature)
        
        val isPng = readLen == 8 &&
                signature[0] == 0x89.toByte() &&
                signature[1] == 0x50.toByte() &&
                signature[2] == 0x4E.toByte() &&
                signature[3] == 0x47.toByte() &&
                signature[4] == 0x0D.toByte() &&
                signature[5] == 0x0A.toByte() &&
                signature[6] == 0x1A.toByte() &&
                signature[7] == 0x0A.toByte()

        if (isPng) {
            try {
                val jsonString = extractPngMetadata(bis)
                if (jsonString != null) {
                    return parseJson(jsonString)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // It's not a PNG, try parsing it directly as raw JSON text
            try {
                bis.reset()
                val rawText = bis.reader(Charsets.UTF_8).readText()
                return parseJson(rawText)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun parseJson(jsonString: String): TavernCharacterData? {
        return try {
            // Tavern format either wraps in a "data" object or places fields in the root
            val root = jsonConfig.decodeFromString<TavernCharacterRoot>(jsonString)
            if (root.data != null) {
                root.data
            } else {
                // Fallback: try parsing root as the data object directly
                jsonConfig.decodeFromString<TavernCharacterData>(jsonString)
            }
        } catch (e: Exception) {
            // Try parsing root directly in case it is structured differently
            try {
                jsonConfig.decodeFromString<TavernCharacterData>(jsonString)
            } catch (e2: Exception) {
                e2.printStackTrace()
                null
            }
        }
    }

    private fun extractPngMetadata(bis: BufferedInputStream): String? {
        val lengthBuffer = ByteArray(4)
        val typeBuffer = ByteArray(4)

        while (true) {
            if (bis.read(lengthBuffer) != 4) break
            val length = ((lengthBuffer[0].toInt() and 0xFF) shl 24) or
                         ((lengthBuffer[1].toInt() and 0xFF) shl 16) or
                         ((lengthBuffer[2].toInt() and 0xFF) shl 8) or
                         (lengthBuffer[3].toInt() and 0xFF)

            if (bis.read(typeBuffer) != 4) break
            val type = String(typeBuffer, Charsets.US_ASCII)

            if (type == "tEXt") {
                val data = ByteArray(length)
                var bytesRead = 0
                while (bytesRead < length) {
                    val r = bis.read(data, bytesRead, length - bytesRead)
                    if (r == -1) break
                    bytesRead += r
                }

                // Find null byte separating keyword and text
                var nullIndex = -1
                for (i in 0 until length) {
                    if (data[i] == 0.toByte()) {
                        nullIndex = i
                        break
                    }
                }

                if (nullIndex != -1) {
                    val keyword = String(data, 0, nullIndex, Charsets.UTF_8)
                    if (keyword == "chara" || keyword == "ccv3") {
                        val text = String(data, nullIndex + 1, length - (nullIndex + 1), Charsets.UTF_8).trim()
                        return tryDecodeBase64OrReturnRaw(text)
                    }
                }
                skipFully(bis, 4) // Skip CRC
            } else if (type == "iTXt") {
                val data = ByteArray(length)
                var bytesRead = 0
                while (bytesRead < length) {
                    val r = bis.read(data, bytesRead, length - bytesRead)
                    if (r == -1) break
                    bytesRead += r
                }

                var nullIndex = -1
                for (i in 0 until length) {
                    if (data[i] == 0.toByte()) {
                        nullIndex = i
                        break
                    }
                }

                if (nullIndex != -1) {
                    val keyword = String(data, 0, nullIndex, Charsets.UTF_8)
                    if (keyword == "chara" || keyword == "ccv3") {
                        val compFlag = data[nullIndex + 1].toInt()
                        
                        // Find next null terminator for Language tag (starts at nullIndex + 3)
                        var langIndex = -1
                        for (i in (nullIndex + 3) until length) {
                            if (data[i] == 0.toByte()) {
                                langIndex = i
                                break
                            }
                        }
                        if (langIndex != -1) {
                            // Find next null terminator for Translated keyword
                            var transIndex = -1
                            for (i in (langIndex + 1) until length) {
                                if (data[i] == 0.toByte()) {
                                    transIndex = i
                                    break
                                }
                            }
                            if (transIndex != -1) {
                                val textStart = transIndex + 1
                                val textLen = length - textStart
                                if (textLen > 0) {
                                    val textBytes = if (compFlag == 1) {
                                        val inflater = Inflater()
                                        inflater.setInput(data, textStart, textLen)
                                        val bos = ByteArrayOutputStream()
                                        val buf = ByteArray(2048)
                                        while (!inflater.finished()) {
                                            val count = inflater.inflate(buf)
                                            if (count == 0) break
                                            bos.write(buf, 0, count)
                                        }
                                        inflater.end()
                                        bos.toByteArray()
                                    } else {
                                        data.copyOfRange(textStart, length)
                                    }
                                    
                                    val textString = String(textBytes, Charsets.UTF_8).trim()
                                    return tryDecodeBase64OrReturnRaw(textString)
                                }
                            }
                        }
                    }
                }
                skipFully(bis, 4) // Skip CRC
            } else if (type == "IEND") {
                break
            } else {
                skipFully(bis, length.toLong() + 4L) // Skip data + CRC
            }
        }
        return null
    }

    private fun tryDecodeBase64OrReturnRaw(text: String): String {
        try {
            val decodedBytes = java.util.Base64.getDecoder().decode(text)
            return String(decodedBytes, Charsets.UTF_8)
        } catch (e: LinkageError) {
            // Fallback for older Android APIs or ClassLoader link failures
        } catch (e: Exception) {
            // Ignore format exceptions and try Android's base64 next
        }

        return try {
            val decodedBytes = Base64.decode(text, Base64.DEFAULT)
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            text
        }
    }

    private fun skipFully(inputStream: InputStream, n: Long) {
        var remaining = n
        val buffer = ByteArray(4096)
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val read = inputStream.read(buffer, 0, toRead)
            if (read == -1) break
            remaining -= read
        }
    }

    fun parseRawJson(inputStream: java.io.InputStream): String? {
        val bis = java.io.BufferedInputStream(inputStream)
        bis.mark(1024 * 1024 * 10) // Mark up to 10MB
        val signature = ByteArray(8)
        val readLen = bis.read(signature)
        val isPng = readLen == 8 &&
                signature[0] == 0x89.toByte() &&
                signature[1] == 0x50.toByte() &&
                signature[2] == 0x4E.toByte() &&
                signature[3] == 0x47.toByte() &&
                signature[4] == 0x0D.toByte() &&
                signature[5] == 0x0A.toByte() &&
                signature[6] == 0x1A.toByte() &&
                signature[7] == 0x0A.toByte()
        return if (isPng) {
            extractPngMetadata(bis)
        } else {
            try {
                bis.reset()
                bis.reader(Charsets.UTF_8).readText()
            } catch (e: Exception) {
                null
            }
        }
    }
}
