package tv.arka.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT = "ArkaTV/0.1 (Android)"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun open(url: String): Response {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("Le serveur a répondu $code")
        }
        return response
    }

    fun getString(url: String): String = open(url).use { it.body?.string() ?: "" }
}
