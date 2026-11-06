package com.agentictv.launcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * The phone remote: a tiny web page the TV serves on your home Wi-Fi.
 *
 * Scan the QR code on the TV (the "Phone" button) and your phone opens
 * http://<tv>:8765/?k=<key>. Type, or use your phone keyboard's dictation mic,
 * and the words go straight to the TV's assistant.
 *
 * Privacy and safety:
 * - It only works on the same Wi-Fi as the TV; nothing goes through the internet
 *   except the question the assistant sends to Claude, as usual.
 * - Every request needs the secret key from the QR code, so other devices on the
 *   network can't control the TV. The key is made on the TV and saved only there.
 */
class PhoneRemote(
    private val context: Context,
    /** Ask the assistant something; returns the reply to show on the phone. */
    private val onAsk: suspend (String) -> String,
) {
    val port = 8765
    val key: String = loadOrMakeKey()
    private var server: ServerSocket? = null
    private val workers = Executors.newFixedThreadPool(2)

    /** The link the QR code points to, or null when the TV has no Wi-Fi address. */
    fun link(): String? = localIp()?.let { "http://$it:$port/?k=$key" }

    fun start() {
        if (server != null) return
        Thread({
            runCatching {
                val socket = ServerSocket(port)
                server = socket
                while (!socket.isClosed) {
                    val client = socket.accept()
                    workers.execute { runCatching { handle(client) }.onFailure { Log.w("AgenticTV", "Phone remote", it) } }
                }
            }.onFailure { if (server?.isClosed != true) Log.w("AgenticTV", "Phone remote stopped", it) }
        }, "phone-remote").apply { isDaemon = true }.start()
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun handle(client: Socket) = client.use { socket ->
        socket.soTimeout = 15_000
        val input = socket.getInputStream().bufferedReader()
        val requestLine = input.readLine() ?: return
        val (method, target) = requestLine.split(" ").let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "/") }
        var length = 0
        while (true) {
            val header = input.readLine() ?: break
            if (header.isEmpty()) break
            if (header.startsWith("content-length:", ignoreCase = true)) length = header.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val body = if (length in 1..4000) CharArray(length).also { var read = 0; while (read < length) { val n = input.read(it, read, length - read); if (n < 0) break; read += n } }.concatToString() else ""

        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&').associate {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
        val out = socket.getOutputStream()
        fun reply(status: String, type: String, text: String) {
            val bytes = text.toByteArray()
            out.write("HTTP/1.1 $status\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(bytes)
            out.flush()
        }

        if (query["k"] != key) return reply("403 Forbidden", "text/plain", "Scan the QR code on the TV to use the phone remote.")
        when {
            method == "GET" && path == "/" -> reply("200 OK", "text/html", PAGE)
            method == "POST" && path == "/ask" -> {
                val text = runCatching { JSONObject(body).optString("text") }.getOrDefault("").trim().take(500)
                val answer = if (text.isEmpty()) "Say or type something first." else runBlocking { onAsk(text) }
                reply("200 OK", "application/json", JSONObject().put("reply", answer).toString())
            }
            method == "POST" && path == "/volume" -> {
                val direction = runCatching { JSONObject(body).optString("direction") }.getOrDefault("")
                runActions(context, listOf(JSONObject().put("type", "change_volume").put("direction", direction)))
                reply("200 OK", "application/json", "{}")
            }
            else -> reply("404 Not Found", "text/plain", "Not found")
        }
    }

    private fun loadOrMakeKey(): String {
        val file = File(context.filesDir, "remote-key.txt")
        runCatching { file.readText().trim() }.getOrNull()?.takeIf { it.length >= 8 }?.let { return it }
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        val made = (1..10).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        runCatching { file.writeText(made) }
        return made
    }

    private fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}

/** The phone page. Plain HTML, no outside files, so it works without internet on the phone. */
private val PAGE = """
<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#0a0a0d"><title>TV remote</title>
<style>
:root{color-scheme:dark}
body{margin:0;min-height:100vh;background:radial-gradient(circle at 30% 20%,#2a3060,#0a0a0d 60%);color:#f5f5f7;
font:16px -apple-system,system-ui,sans-serif;display:flex;flex-direction:column;padding:28px 20px;box-sizing:border-box;gap:18px}
h1{font-weight:600;font-size:26px;margin:8px 0 0}
p{color:#9a9aa2;margin:0;line-height:1.4}
textarea{width:100%;box-sizing:border-box;min-height:110px;border-radius:18px;border:1px solid #ffffff2e;background:#ffffff14;
color:#f5f5f7;font-size:18px;font-family:inherit;padding:16px;resize:none}
button{border:0;border-radius:999px;font:600 17px -apple-system,system-ui,sans-serif;padding:16px;cursor:pointer}
.send{background:#fff;color:#0a0a0d}
.row{display:flex;gap:10px}.row button{flex:1;background:#ffffff1f;color:#f5f5f7}
#reply{min-height:24px;color:#f5f5f7;font-size:17px}
</style></head><body>
<h1>✦ Ask the TV</h1>
<p>Tap the box, then the 🎤 on your keyboard and say what you'd like. For example “open Netflix” or “find cat videos”.</p>
<textarea id="t" placeholder="Say or type here…"></textarea>
<button class="send" id="s">Send to TV</button>
<div id="reply"></div>
<div class="row"><button data-v="down">🔉</button><button data-v="mute">🔇</button><button data-v="up">🔊</button></div>
<script>
const k=new URLSearchParams(location.search).get('k'),t=document.getElementById('t'),r=document.getElementById('reply'),s=document.getElementById('s');
async function post(p,b){const x=await fetch(p+'?k='+encodeURIComponent(k),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(b)});return x.json()}
async function ask(){const q=t.value.trim();if(!q)return;s.disabled=true;r.textContent='Thinking…';
try{const a=await post('/ask',{text:q});r.textContent=a.reply;t.value=''}catch(e){r.textContent='Couldn’t reach the TV. Are you on the same Wi-Fi?'}s.disabled=false}
s.onclick=ask;t.addEventListener('keydown',e=>{if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();ask()}});
document.querySelectorAll('[data-v]').forEach(b=>b.onclick=()=>post('/volume',{direction:b.dataset.v}).catch(()=>{}));
</script></body></html>
""".trimIndent()
