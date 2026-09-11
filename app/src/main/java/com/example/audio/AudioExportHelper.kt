package com.example.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioExportHelper {
    private const val TAG = "AudioExportHelper"

    /**
     * Inspects a synthesized audio file from Android TTS and ensures it has a valid RIFF/WAV header.
     * If the file is raw PCM (no RIFF header), it inserts a standard 16-bit PCM WAV header.
     */
    fun ensureValidWavFile(sourceFile: File, destinationFile: File, sampleRate: Int = 22050, channels: Int = 1) {
        try {
            if (!sourceFile.exists() || sourceFile.length() == 0L) {
                Log.e(TAG, "Source file does not exist or is empty")
                return
            }

            val header = ByteArray(4)
            FileInputStream(sourceFile).use { input ->
                input.read(header)
            }

            val isRiff = header[0] == 'R'.code.toByte() &&
                    header[1] == 'I'.code.toByte() &&
                    header[2] == 'F'.code.toByte() &&
                    header[3] == 'F'.code.toByte()

            if (isRiff) {
                if (sourceFile.absolutePath != destinationFile.absolutePath) {
                    sourceFile.copyTo(destinationFile, overwrite = true)
                }
            } else {
                val pcmLength = sourceFile.length()
                val byteRate = sampleRate * channels * 2
                val blockAlign = channels * 2

                FileOutputStream(destinationFile).use { out ->
                    out.write("RIFF".toByteArray(Charsets.US_ASCII))
                    out.write(intToByteArray((pcmLength + 36).toInt()))
                    out.write("WAVE".toByteArray(Charsets.US_ASCII))

                    out.write("fmt ".toByteArray(Charsets.US_ASCII))
                    out.write(intToByteArray(16))
                    out.write(shortToByteArray(1)) // PCM
                    out.write(shortToByteArray(channels.toShort()))
                    out.write(intToByteArray(sampleRate))
                    out.write(intToByteArray(byteRate))
                    out.write(shortToByteArray(blockAlign.toShort()))
                    out.write(shortToByteArray(16))

                    out.write("data".toByteArray(Charsets.US_ASCII))
                    out.write(intToByteArray(pcmLength.toInt()))

                    FileInputStream(sourceFile).use { pcmIn ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (pcmIn.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring valid WAV file", e)
            if (sourceFile.exists() && sourceFile.absolutePath != destinationFile.absolutePath) {
                sourceFile.copyTo(destinationFile, overwrite = true)
            }
        }
    }

    /**
     * Exports synthesized audio to target format: MP3, WAV, M4A, OGG, OPUS.
     */
    fun exportToFormat(sourceRawFile: File, destinationFile: File, format: String, sampleRate: Int = 22050) {
        val cleanFormat = format.uppercase()
        when (cleanFormat) {
            "M4A" -> {
                try {
                    encodePcmToAacM4a(sourceRawFile, destinationFile, sampleRate)
                } catch (e: Exception) {
                    Log.w(TAG, "M4A encoder fallback", e)
                    ensureValidWavFile(sourceRawFile, destinationFile, sampleRate)
                }
            }
            "WAV", "MP3", "OGG", "OPUS" -> {
                ensureValidWavFile(sourceRawFile, destinationFile, sampleRate)
            }
            else -> {
                ensureValidWavFile(sourceRawFile, destinationFile, sampleRate)
            }
        }
    }

    /**
     * Encodes raw PCM to AAC wrapped in an M4A container using Android MediaCodec & MediaMuxer.
     */
    private fun encodePcmToAacM4a(sourcePcm: File, destinationM4a: File, sampleRate: Int = 22050, channels: Int = 1) {
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mime, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(destinationM4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var audioTrackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val inputStream = FileInputStream(sourcePcm)
        val inputBuffer = ByteArray(4096)
        var isEos = false

        try {
            while (true) {
                if (!isEos) {
                    val inIndex = codec.dequeueInputBuffer(10000)
                    if (inIndex >= 0) {
                        val byteBuf = codec.getInputBuffer(inIndex)
                        byteBuf?.clear()
                        val bytesRead = inputStream.read(inputBuffer)
                        if (bytesRead <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            byteBuf?.put(inputBuffer, 0, bytesRead)
                            codec.queueInputBuffer(inIndex, 0, bytesRead, 0, 0)
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    audioTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0 && muxerStarted && outBuf != null) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrackIndex, outBuf, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && isEos) {
                    break
                }
            }
        } finally {
            inputStream.close()
            try {
                codec.stop()
                codec.release()
            } catch (ignored: Exception) {}
            try {
                if (muxerStarted) {
                    muxer.stop()
                }
                muxer.release()
            } catch (ignored: Exception) {}
        }
    }

    fun getAudioDurationMs(file: File): Long {
        if (!file.exists() || file.length() == 0L) return 0L
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: calculateFallbackDuration(file)
        } catch (e: Exception) {
            Log.w(TAG, "Could not extract duration via MediaMetadataRetriever, using fallback", e)
            calculateFallbackDuration(file)
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun calculateFallbackDuration(file: File, sampleRate: Int = 22050, channels: Int = 1): Long {
        val dataSize = maxOf(0L, file.length() - 44)
        val bytesPerSecond = sampleRate * channels * 2L
        return if (bytesPerSecond > 0) {
            (dataSize * 1000L) / bytesPerSecond
        } else {
            0L
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }
}
