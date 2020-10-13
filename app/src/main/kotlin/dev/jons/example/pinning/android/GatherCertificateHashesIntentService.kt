package dev.jons.example.pinning.android

import android.app.IntentService
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateEncodingException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

class GatherCertificateHashesIntentService : IntentService("RetrieveCertIstApiResults") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val action = intent.action
            val hostName = intent.getStringExtra(EXTRAS_HOST_NAME)
            if (hostName != null) {
                when {
                    ACTION_GET_CERTIST_HASHES == action -> {
                        getCertificateHashesFromCertIstApi(hostName)
                    }
                    ACTION_GET_LOCAL_SOCKET_HASHES == action -> {
                        getCertificateHashesFromLocalSocket(hostName)
                    }
                    else -> {
                        throw UnsupportedOperationException(String.format("ERROR UNKNOWN ACTION: %s", action))
                    }
                }
            }
        }
    }

    private fun getCertificateHashesFromCertIstApi(hostName: String) {
        try {
            val format = String.format("https://api.cert.ist/%s", hostName)
            val urlConnection = URL(format).openConnection() as HttpsURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.readTimeout = 10 * 1000
            urlConnection.connectTimeout = 10 * 1000
            urlConnection.sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            urlConnection.connect()
            val total = StringBuilder()
            urlConnection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        total.append(line).append('\n')
                    }
                }
            }
            val root = JSONObject(total.toString())
            val chain = root.getJSONArray("chain")
            val sha256Hashes = arrayOfNulls<String>(chain.length())
            (0 until chain.length()).forEach { i ->
                val certificateInChain = chain.getJSONObject(i)
                val der = certificateInChain.getJSONObject("der")
                val hashes = der.getJSONObject("hashes")
                sha256Hashes[i] = hashes.getString("sha256")
            }
            val data = Intent(ACTION_RESULT_CERTIST_HASHES)
            data.putExtra(RESULT_SHA256_HASHES, sha256Hashes)
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(data)
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun getCertificateHashesFromLocalSocket(hostName: String) {
        try {
            val url = URL(String.format("https://%s", hostName))
            val urlConnection = url.openConnection() as HttpsURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.readTimeout = 10 * 1000
            urlConnection.connectTimeout = 10 * 1000
            urlConnection.sslSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
            urlConnection.connect()
            val hashes = arrayOfNulls<String>(urlConnection.serverCertificates.size)
            urlConnection.serverCertificates.indices.forEach { i ->
                val certificate = urlConnection.serverCertificates[i]
                val encoded = certificate.encoded
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(encoded)
                val hex = StringBuilder(hash.size * 2)
                for (b in hash) hex.append(String.format("%02x", b))
                hashes[i] = hex.toString()
            }
            val data = Intent(ACTION_RESULT_LOCAL_SOCKET_HASHES)
            data.putExtra(RESULT_SHA256_HASHES, hashes)
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(data)
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: CertificateEncodingException) {
            e.printStackTrace()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
    }

    companion object {
        const val ACTION_GET_CERTIST_HASHES = "dev.jons.example.pinning.android.action.GET_CERTIST_HASHES"
        const val ACTION_GET_LOCAL_SOCKET_HASHES = "dev.jons.example.pinning.android.action.GET_LOCAL_SOCKET_HASHES"
        const val ACTION_RESULT_LOCAL_SOCKET_HASHES = "dev.jons.example.pinning.android.action.RESULT_LOCAL_SOCKET_HASHES"
        const val ACTION_RESULT_CERTIST_HASHES = "dev.jons.example.pinning.android.action.RESULT_CERTIST_HASHES"
        const val EXTRAS_HOST_NAME = "dev.jons.example.pinning.android.extra.HOST_NAME"
        const val RESULT_SHA256_HASHES = "dev.jons.example.pinning.android.result.RESULT_SHA256_HASHES"
        @JvmStatic
        fun getCertistApiHashesForDomain(context: Context, domain: String?) {
            val intent = Intent(context, GatherCertificateHashesIntentService::class.java)
            intent.action = ACTION_GET_CERTIST_HASHES
            intent.putExtra(EXTRAS_HOST_NAME, domain)
            context.startService(intent)
        }

        @JvmStatic
        fun getLocalSocketHashesForDomain(context: Context, domain: String?) {
            val intent = Intent(context, GatherCertificateHashesIntentService::class.java)
            intent.action = ACTION_GET_LOCAL_SOCKET_HASHES
            intent.putExtra(EXTRAS_HOST_NAME, domain)
            context.startService(intent)
        }
    }
}