package cl.gakuto.kizuna.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Artwork loader with a bounded queue so rapid scrolling cannot accumulate thousands of jobs. */
object ArtworkLoader {
    private val executor = ThreadPoolExecutor(
        2,
        2,
        30L,
        TimeUnit.SECONDS,
        LinkedBlockingDeque(40),
        ThreadPoolExecutor.DiscardOldestPolicy(),
    )
    private val cache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(
        context: Context,
        view: ImageView,
        candidates: List<String>,
        placeholder: Int,
        onLoaded: (() -> Unit)? = null,
    ) {
        val requestKey = candidates.joinToString("|")
        view.tag = requestKey
        view.setImageResource(placeholder)
        view.visibility = View.VISIBLE
        if (candidates.isEmpty()) return

        candidates.firstNotNullOfOrNull { cache.get(it) }?.let { bitmap ->
            if (view.tag == requestKey) {
                view.setImageBitmap(bitmap)
                onLoaded?.invoke()
            }
            return
        }

        executor.execute {
            val loaded = candidates.firstNotNullOfOrNull { candidate ->
                cache.get(candidate) ?: decode(context, candidate)?.also { cache.put(candidate, it) }
            }
            if (loaded != null) {
                view.post {
                    if (view.tag == requestKey) {
                        view.setImageBitmap(loaded)
                        onLoaded?.invoke()
                    }
                }
            }
        }
    }

    private fun decode(context: Context, source: String): Bitmap? = runCatching {
        val uri = Uri.parse(source)
        when (uri.scheme?.lowercase()) {
            "content", "file" -> context.contentResolver.openInputStream(uri)?.use(::decodeSampled)
            "http", "https" -> {
                val connection = URL(source).openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "OzRetroRom/0.6.0-beta.4")
                try {
                    if (connection.responseCode !in 200..299) null
                    else connection.inputStream.use(::decodeSampled)
                } finally {
                    connection.disconnect()
                }
            }
            else -> null
        }
    }.getOrNull()

    private fun decodeSampled(stream: InputStream): Bitmap? {
        val bytes = stream.readBytes()
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSample(bounds.outWidth, bounds.outHeight, TARGET_PIXELS)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun calculateSample(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / sample > target * 2 || height / sample > target * 2) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private const val TARGET_PIXELS = 320
}
