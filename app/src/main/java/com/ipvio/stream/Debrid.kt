package com.ipvio.stream

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class DebridService(val label: String) {
    REAL_DEBRID("Real-Debrid"),
    TORBOX("TorBox")
}

sealed class DebridResult {
    class Ok(val url: String) : DebridResult()
    class Error(val message: String) : DebridResult()
}

object DebridPrefs {
    private const val FILE = "ipvio_debrid"

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Volatile var realDebrid: String = ""
        private set
    @Volatile var torBox: String = ""
        private set
    private var inited = false

    @Synchronized
    fun init(c: Context) {
        if (inited) return
        inited = true
        val p = prefs(c)
        realDebrid = clean(p.getString("realdebrid", "").orEmpty())
        torBox = clean(p.getString("torbox", "").orEmpty())
    }

    fun clean(s: String): String =
        s.trim().filter { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }.take(200)

    fun save(c: Context, realDebridKey: String, torBoxKey: String) {
        realDebrid = clean(realDebridKey)
        torBox = clean(torBoxKey)
        prefs(c).edit().putString("realdebrid", realDebrid).putString("torbox", torBox).apply()
        inited = true
    }

    fun key(service: DebridService): String = when (service) {
        DebridService.REAL_DEBRID -> realDebrid
        DebridService.TORBOX -> torBox
    }

    fun configured(): Boolean = realDebrid.isNotEmpty() || torBox.isNotEmpty()

    fun services(): List<DebridService> = buildList {
        if (realDebrid.isNotEmpty()) add(DebridService.REAL_DEBRID)
        if (torBox.isNotEmpty()) add(DebridService.TORBOX)
    }
}

object Debrid {
    private const val RD = "https://api.real-debrid.com/rest/1.0"
    private const val TB = "https://api.torbox.app/v1/api"
    private const val WAIT_MS = 180_000L

    private val VIDEO = listOf(".mkv", ".mp4", ".avi", ".m4v", ".mov", ".ts", ".webm", ".wmv", ".mpg", ".mpeg")
    private val SPANISH = listOf(
        "castellano", "espanol", "español", "spanish", "latino", "latina", "spa", "esp", "dual"
    )

    fun isLink(s: String): Boolean {
        val v = s.trim().lowercase()
        return v.startsWith("magnet:") || (v.startsWith("http") && (v.contains(".torrent") || v.contains("magnet")))
    }

    suspend fun check(service: DebridService, key: String): String? = withContext(Dispatchers.IO) {
        try {
            val r = when (service) {
                DebridService.REAL_DEBRID -> http("GET", "$RD/user", auth(key))
                DebridService.TORBOX -> http("GET", "$TB/user/me", auth(key))
            }
            when {
                r.code == 200 -> null
                r.code == 401 || r.code == 403 -> "Schlüssel ungültig"
                else -> "Server antwortet mit Fehler ${r.code}"
            }
        } catch (e: UnknownHostException) {
            "Server nicht gefunden (DNS)"
        } catch (e: SocketTimeoutException) {
            "Zeitüberschreitung, Server antwortet nicht"
        } catch (e: Exception) {
            "Keine Verbindung: " + e.javaClass.simpleName
        }
    }

    suspend fun resolve(c: Context, service: DebridService, input: String, onStatus: (String) -> Unit): DebridResult =
        withContext(Dispatchers.IO) {
            try {
                DebridPrefs.init(c)
                val key = DebridPrefs.key(service)
                if (key.isEmpty()) return@withContext DebridResult.Error("Kein Schlüssel für ${service.label}")
                val link = input.trim()
                if (!isLink(link)) return@withContext DebridResult.Error("Ungültiger Link. Magnet- oder .torrent-Adresse einfügen.")
                when (service) {
                    DebridService.REAL_DEBRID -> realDebrid(key, link, onStatus)
                    DebridService.TORBOX -> torBox(key, link, onStatus)
                }
            } catch (e: UnknownHostException) {
                DebridResult.Error("Server nicht gefunden (DNS)")
            } catch (e: SocketTimeoutException) {
                DebridResult.Error("Zeitüberschreitung, Server antwortet nicht")
            } catch (e: Exception) {
                DebridResult.Error("Keine Verbindung: " + e.javaClass.simpleName)
            }
        }

    private fun auth(key: String) = mapOf("Authorization" to "Bearer $key")

    private suspend fun realDebrid(key: String, link: String, onStatus: (String) -> Unit): DebridResult {
        onStatus("Wird zu Real-Debrid hinzugefügt …")
        val add = if (link.startsWith("magnet:", true)) {
            http("POST", "$RD/torrents/addMagnet", auth(key), FormBody.Builder().add("magnet", link).build())
        } else {
            val bytes = download(link) ?: return DebridResult.Error("Torrent-Datei konnte nicht geladen werden.")
            http("POST", "$RD/torrents/addTorrent", auth(key), bytes.toRequestBody("application/x-bittorrent".toMediaType()))
        }
        val added = json(add.body)
        val id = added?.let { s(it, "id") }
            ?: return DebridResult.Error(if (add.code in 400..499) "Der Dienst hat den Torrent nicht angenommen." else "Server antwortet mit Fehler ${add.code}")

        val deadline = System.currentTimeMillis() + WAIT_MS
        var selected = false
        var last = ""
        while (System.currentTimeMillis() < deadline) {
            val info = json(http("GET", "$RD/torrents/info/$id", auth(key)).body)
                ?: return DebridResult.Error("Der Dienst hat keine Torrent-Info geliefert.")
            val status = info.optString("status")
            if (status != last) {
                last = status
                onStatus(rdStatus(status))
            }
            when (status) {
                "waiting_files_selection" -> {
                    if (!selected) {
                        selected = true
                        val file = bestFile(info.optJSONArray("files"), "id", "path", "bytes")
                        val files = file?.first ?: "all"
                        http("POST", "$RD/torrents/selectFiles/$id", auth(key), FormBody.Builder().add("files", files).build())
                    }
                }
                "downloaded" -> {
                    val links = info.optJSONArray("links")
                    if (links == null || links.length() == 0) return DebridResult.Error("Kein abspielbarer Link gefunden.")
                    for (i in 0 until links.length()) {
                        val direct = unrestrict(key, links.optString(i))
                        if (direct != null) return DebridResult.Ok(direct)
                    }
                    return DebridResult.Error("Kein abspielbarer Link gefunden.")
                }
                "magnet_error", "error", "virus", "dead" ->
                    return DebridResult.Error("Der Dienst hat den Torrent nicht angenommen.")
            }
            delay(2000)
        }
        return DebridResult.Error("Zeitüberschreitung. Der Torrent wird noch geladen – später erneut versuchen.")
    }

    private fun unrestrict(key: String, link: String): String? {
        if (link.isBlank()) return null
        val r = http("POST", "$RD/unrestrict/link", auth(key), FormBody.Builder().add("link", link).build())
        if (r.code != 200) return null
        return json(r.body)?.let { s(it, "download") }?.takeIf { it.startsWith("http") }
    }

    private suspend fun torBox(key: String, link: String, onStatus: (String) -> Unit): DebridResult {
        onStatus("Wird zu TorBox hinzugefügt …")
        val create = if (link.startsWith("magnet:", true)) {
            http(
                "POST", "$TB/torrents/createtorrent", auth(key),
                FormBody.Builder().add("magnet", link).add("seed", "1").build()
            )
        } else {
            val bytes = download(link) ?: return DebridResult.Error("Torrent-Datei konnte nicht geladen werden.")
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "upload.torrent", bytes.toRequestBody("application/x-bittorrent".toMediaType()))
                .addFormDataPart("seed", "1")
                .build()
            http("POST", "$TB/torrents/createtorrent", auth(key), body)
        }
        val created = json(create.body)
        val id = created?.let { torrentId(it) }
            ?: return DebridResult.Error(if (create.code in 400..499) "Der Dienst hat den Torrent nicht angenommen." else "Server antwortet mit Fehler ${create.code}")

        val deadline = System.currentTimeMillis() + WAIT_MS
        var last = ""
        while (System.currentTimeMillis() < deadline) {
            val list = json(http("GET", "$TB/torrents/mylist?id=$id&bypass_cache=true", auth(key)).body)
                ?: return DebridResult.Error("Der Dienst hat keine Torrent-Info geliefert.")
            val torrent = dataObject(list) ?: return DebridResult.Error("Der Dienst hat keine Torrent-Info geliefert.")
            val state = s(torrent, "download_state") ?: ""
            val finished = torrent.optBoolean("download_finished", false) ||
                state.equals("completed", true) || state.equals("cached", true) || state.equals("uploading", true)
            if (state != last) {
                last = state
                onStatus(tbStatus(state))
            }
            if (finished) {
                val file = bestFile(torrent.optJSONArray("files"), "id", "name", "size")
                    ?: return DebridResult.Error("Keine Dateien in diesem Torrent gefunden.")
                onStatus("Download-Link wird erstellt …")
                val direct = tbDownload(key, id, file.first)
                    ?: return DebridResult.Error("Kein abspielbarer Link gefunden.")
                return DebridResult.Ok(direct)
            }
            if (state.contains("error", true) && !state.contains("stalled", true)) {
                return DebridResult.Error("Der Dienst hat den Torrent nicht angenommen.")
            }
            delay(2000)
        }
        return DebridResult.Error("Zeitüberschreitung. Der Torrent wird noch geladen – später erneut versuchen.")
    }

    private fun tbDownload(key: String, torrentId: String, fileId: String): String? {
        val url = "$TB/torrents/requestdl?token=$key&torrent_id=$torrentId&file_id=$fileId"
        val r = http("GET", url, auth(key), follow = false)
        if (r.code in 300..399) return r.location?.takeIf { it.startsWith("http") }
        if (r.code !in 200..299) return null
        val body = r.body.trim()
        if (body.startsWith("http")) return body
        val o = json(body) ?: return null
        return when (val d = o.opt("data")) {
            is String -> d.takeIf { it.startsWith("http") }
            is JSONObject -> (s(d, "url") ?: s(d, "download") ?: s(d, "link"))?.takeIf { it.startsWith("http") }
            else -> (s(o, "url") ?: s(o, "download"))?.takeIf { it.startsWith("http") }
        }
    }

    private fun rdStatus(status: String): String = when (status) {
        "magnet_conversion" -> "Torrent wird vorbereitet …"
        "waiting_files_selection" -> "Dateien werden ausgewählt …"
        "queued" -> "In der Warteschlange …"
        "downloading" -> "Torrent wird geladen …"
        "compressing", "uploading" -> "Fast fertig …"
        "downloaded" -> "Download-Link wird erstellt …"
        else -> "Stream wird vorbereitet …"
    }

    private fun tbStatus(state: String): String = when {
        state.contains("stalled", true) -> "Keine Seeds – warte auf Peers …"
        state.contains("queued", true) -> "In der Warteschlange …"
        state.contains("downloading", true) -> "Torrent wird geladen …"
        state.contains("uploading", true) -> "Fast fertig …"
        state.contains("error", true) -> "Der Dienst meldet einen Fehler."
        else -> "Stream wird vorbereitet …"
    }

    private fun bestFile(files: JSONArray?, idKey: String, nameKey: String, sizeKey: String): Pair<String, String>? {
        if (files == null) return null
        var best: Pair<String, String>? = null
        var bestScore = Long.MIN_VALUE
        for (i in 0 until files.length()) {
            val f = files.optJSONObject(i) ?: continue
            val id = s(f, idKey) ?: continue
            val name = s(f, nameKey) ?: continue
            val size = f.optLong(sizeKey, 0L)
            val score = fileScore(name, size)
            if (score > bestScore) {
                bestScore = score
                best = id to name
            }
        }
        return if (bestScore >= 0) best else null
    }

    private fun fileScore(name: String, size: Long): Long {
        val n = name.lowercase()
        val video = VIDEO.any { n.endsWith(it) || n.contains("$it ") || n.contains("$it.") }
        if (!video) return -1
        var score = size / 1_000_000L
        if (SPANISH.any { n.contains(it) }) score += 1_000_000_000L
        if (n.contains("sample") || n.contains("trailer")) score -= 500_000_000L
        return score
    }

    private fun torrentId(o: JSONObject): String? {
        val d = o.opt("data")
        return when (d) {
            is JSONObject -> s(d, "torrent_id") ?: s(d, "torrentId") ?: s(d, "id")
            is JSONArray -> if (d.length() > 0) (d.opt(0) as? JSONObject)?.let {
                s(it, "torrent_id") ?: s(it, "torrentId") ?: s(it, "id")
            } else null
            is Number -> d.toString()
            is String -> d.takeIf { it.isNotBlank() }
            else -> s(o, "torrent_id")
        }
    }

    private fun dataObject(o: JSONObject): JSONObject? = when (val d = o.opt("data")) {
        is JSONObject -> d
        is JSONArray -> if (d.length() > 0) d.optJSONObject(0) else null
        else -> null
    }

    private fun s(o: JSONObject, key: String): String? = o.optString(key).takeIf { it.isNotBlank() }

    private fun json(body: String): JSONObject? = runCatching { JSONObject(body) }.getOrNull()

    private fun download(url: String): ByteArray? {
        val r = http("GET", url, mapOf("User-Agent" to "IPVIO-Stream"))
        if (r.code !in 200..299) return null
        return r.raw?.takeIf { it.size in 1..5_000_000 }
    }

    private class Resp(val code: Int, val body: String, val location: String?, val raw: ByteArray?)

    private fun http(
        method: String, url: String, headers: Map<String, String>,
        body: RequestBody? = null, follow: Boolean = true
    ): Resp {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        if (method == "POST") b.post(body ?: ByteArray(0).toRequestBody(null)) else b.get()
        val client = if (follow) Net.base else Net.base.newBuilder().followRedirects(false).build()
        client.newCall(b.build()).execute().use { r ->
            val raw = r.body?.bytes()
            val text = raw?.toString(Charsets.UTF_8).orEmpty()
            return Resp(r.code, text, r.header("Location"), raw)
        }
    }
}
