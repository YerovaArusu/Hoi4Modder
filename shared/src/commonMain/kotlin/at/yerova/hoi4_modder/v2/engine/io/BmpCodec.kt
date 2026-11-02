package at.yerova.hoi4_modder.v2.engine.io

import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

data class DecodedMapBitmap(
    val image: BufferedImage,
    val pixels: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecodedMapBitmap

        if (image != other.image) return false
        if (!pixels.contentEquals(other.pixels)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = image.hashCode()
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

object BmpCodec {

    fun loadBitmap(file: File): DecodedMapBitmap {
        require(file.exists()) { "Missing provinces.bmp: ${file.absolutePath}" }
        val loaded = ImageIO.read(file)
            ?: error("Could not decode provinces.bmp: ${file.absolutePath}")

        val rgbImage = BufferedImage(loaded.width, loaded.height, BufferedImage.TYPE_INT_RGB)
        rgbImage.createGraphics().useGraphics { g ->
            g.drawImage(loaded, 0, 0, null)
        }
        val pixels = (rgbImage.raster.dataBuffer as DataBufferInt).data
        return DecodedMapBitmap(rgbImage, pixels)
    }

    /**
     * Schreibt ein unkomprimiertes 24-Bit Little-Endian BMP exakt so, wie die Clausewitz-Engine es erwartet.
     */
    fun saveProvincesBmp(targetFile: File, width: Int, height: Int, mapPixels: IntArray) {
        targetFile.parentFile?.mkdirs()

        val rowStride = width * 3
        val padding = (4 - (rowStride % 4)) % 4
        val paddedRowSize = rowStride + padding
        val imageSize = paddedRowSize * height
        val fileSize = 54 + imageSize

        BufferedOutputStream(FileOutputStream(targetFile), 65536).use { out ->
            val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN)
            header.put('B'.code.toByte())
            header.put('M'.code.toByte())
            header.putInt(fileSize)
            header.putShort(0)
            header.putShort(0)
            header.putInt(54)

            header.putInt(40)
            header.putInt(width)
            header.putInt(height)
            header.putShort(1)
            header.putShort(24)
            header.putInt(0)
            header.putInt(imageSize)
            header.putInt(2835)
            header.putInt(2835)
            header.putInt(0)
            header.putInt(0)

            out.write(header.array())

            val rowBuffer = ByteArray(paddedRowSize)

            for (y in (height - 1) downTo 0) {
                val rowStart = y * width
                var idx = 0
                for (x in 0 until width) {
                    val rgb = mapPixels[rowStart + x]
                    rowBuffer[idx++] = (rgb and 0xFF).toByte()
                    rowBuffer[idx++] = ((rgb shr 8) and 0xFF).toByte()
                    rowBuffer[idx++] = ((rgb shr 16) and 0xFF).toByte()
                }
                for (p in 0 until padding) {
                    rowBuffer[idx++] = 0
                }
                out.write(rowBuffer)
            }
        }
    }

    private inline fun <T> Graphics2D.useGraphics(block: (Graphics2D) -> T): T {
        return try {
            block(this)
        } finally {
            dispose()
        }
    }
}