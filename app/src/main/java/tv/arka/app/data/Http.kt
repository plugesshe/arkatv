package tv.arka.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT = "ArkaTV/0.2 (Android)"

    /** The server answered, but not with a success: [code] says how. */
    class StatusException(val code: Int) : IOException("Le serveur a répondu $code")

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** [bearer] is only ever sent to the address being requested, never logged. */
    fun open(url: String, bearer: String? = null): Response {
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer").header("Accept", "application/json")
        val response = client.newCall(builder.build()).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw StatusException(code)
        }
        return response
    }

    fun getString(url: String, bearer: String? = null): String = open(url, bearer).use { it.body?.string() ?: "" }
}
