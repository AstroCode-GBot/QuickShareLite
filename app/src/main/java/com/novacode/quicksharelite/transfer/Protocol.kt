package com.novacode.quicksharelite.transfer

import org.json.JSONArray
import org.json.JSONObject

internal object Protocol {
    const val MANIFEST = "manifest"
    const val ACCEPT = "accept"
    const val REJECT = "reject"

    fun manifest(sessionId: String, senderName: String, name: String, size: Long, mime: String?, sha256: String): ByteArray =
        JSONObject()
            .put("type", MANIFEST)
            .put("sessionId", sessionId)
            .put("senderName", senderName)
            .put("files", JSONArray().put(JSONObject().put("name", name).put("size", size).put("mime", mime ?: "").put("sha256", sha256)))
            .toString().toByteArray(Charsets.UTF_8)

    fun command(type: String, sessionId: String): ByteArray =
        JSONObject().put("type", type).put("sessionId", sessionId).toString().toByteArray(Charsets.UTF_8)

    fun parse(data: ByteArray): JSONObject = JSONObject(String(data, Charsets.UTF_8))
}
