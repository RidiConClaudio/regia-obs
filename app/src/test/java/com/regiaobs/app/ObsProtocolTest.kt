package com.regiaobs.app

import com.regiaobs.app.obs.ObsAuth
import com.regiaobs.app.obs.ObsErrorMapper
import com.regiaobs.app.obs.ObsEventSubs
import com.regiaobs.app.obs.ObsJson
import com.regiaobs.app.obs.ObsOpCode
import com.regiaobs.app.obs.ObsUriParser
import com.regiaobs.app.obs.int
import com.regiaobs.app.obs.obj
import com.regiaobs.app.obs.str
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

class ObsProtocolTest {

    @Test
    fun `computeAuth produces exact obs-websocket v5 SHA256 base64 chain`() {
        val password = "supersecretpassword"
        val salt = "salt123"
        val challenge = "challenge456"

        val md = MessageDigest.getInstance("SHA-256")
        val sec = Base64.getEncoder().encodeToString(
            md.digest((password + salt).toByteArray(StandardCharsets.UTF_8))
        )
        val expected = Base64.getEncoder().encodeToString(
            md.digest((sec + challenge).toByteArray(StandardCharsets.UTF_8))
        )

        val actual = ObsAuth.computeAuth(password, salt, challenge)
        assertEquals(expected, actual)
        assertNotEquals(actual, ObsAuth.computeAuth("wrong", salt, challenge))
    }

    @Test
    fun `parse handles obsws QR URI with encoded password`() {
        val profile = ObsUriParser.parse("obsws://192.168.1.50:4455/my%20pass%21%40")
        assertNotNull(profile)
        assertEquals("192.168.1.50", profile!!.host)
        assertEquals(4455, profile.port)
        assertEquals("my pass!@", profile.password)
        assertFalse(profile.useTls)
    }

    @Test
    fun `parse handles ws, wss, and plain host formats`() {
        val plain = ObsUriParser.parse("192.168.0.10")
        assertNotNull(plain)
        assertEquals("192.168.0.10", plain!!.host)
        assertEquals(4455, plain.port)

        val wss = ObsUriParser.parse("wss://studio.local:4499")
        assertNotNull(wss)
        assertEquals("studio.local", wss!!.host)
        assertEquals(4499, wss.port)
        assertTrue(wss.useTls)

        assertNull(ObsUriParser.parse(""))
        assertNull(ObsUriParser.parse("   "))
        assertNull(ObsUriParser.parse("ws://192.168.1.1:99999"))
    }

    @Test
    fun `buildIdentify and buildRequest serialize expected opcodes`() {
        val identify = ObsJson.codec.parseToJsonElement(
            ObsJson.buildIdentify(rpcVersion = 1, authentication = "abc", eventSubscriptions = ObsEventSubs.ALL_WITH_METERS)
        ).jsonObject
        assertEquals(ObsOpCode.IDENTIFY, identify.int("op"))
        assertEquals(1, identify.obj("d")!!.int("rpcVersion"))
        assertEquals("abc", identify.obj("d")!!.str("authentication"))

        val req = ObsJson.codec.parseToJsonElement(
            ObsJson.buildRequest(
                requestType = "SetCurrentProgramScene",
                requestId = "req-1",
                requestData = buildJsonObject { put("sceneName", "Gaming") }
            )
        ).jsonObject
        assertEquals(ObsOpCode.REQUEST, req.int("op"))
        assertEquals("SetCurrentProgramScene", req.obj("d")!!.str("requestType"))
        assertEquals("req-1", req.obj("d")!!.str("requestId"))
        assertEquals("Gaming", req.obj("d")!!.obj("requestData")!!.str("sceneName"))
    }

    @Test
    fun `error mapper translates standard obs-websocket v5 codes into English`() {
        assertTrue(ObsErrorMapper.describe(506, "TriggerStudioModeTransition", null).contains("Studio Mode"))
        assertTrue(ObsErrorMapper.describe(501, "ToggleRecordPause", null).contains("recording"))
        assertTrue(ObsErrorMapper.describe(504, "SaveReplayBuffer", null).contains("Replay Buffer"))
        assertTrue(ObsErrorMapper.describe(600, "ToggleInputMute", "No source").contains("Sync Deck"))
        assertTrue(ObsErrorMapper.describe(500, "StartStream", null).contains("already running"))
    }
}
