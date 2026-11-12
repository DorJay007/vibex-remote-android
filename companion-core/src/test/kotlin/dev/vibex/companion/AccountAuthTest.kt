package dev.vibex.companion

import dev.vibex.remote.v1.JsonValue
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountAuthTest {
    private fun parse(body: String): Map<String, JsonValue> = (JsonValue.parse(body) as JsonValue.Object).value

    @Test
    fun passwordLoginBody() {
        val body = parse(AccountAuthRequests.passwordLogin("  alice@example.com ", " secret 1 "))
        assertEquals(JsonValue.Text("password"), body["mode"])
        assertEquals(JsonValue.Text("alice@example.com"), body["login"])
        assertEquals(JsonValue.Text(" secret 1 "), body["password"])
        assertEquals(3, body.size)
    }

    @Test
    fun codeLoginBody() {
        val body = parse(AccountAuthRequests.codeLogin("13800000000", " 123456 "))
        assertEquals(JsonValue.Text("code"), body["mode"])
        assertEquals(JsonValue.Text("13800000000"), body["login"])
        assertEquals(JsonValue.Text("123456"), body["code"])
        assertEquals(3, body.size)
    }

    @Test
    fun sendLoginCodeBody() {
        val body = parse(AccountAuthRequests.sendLoginCode(" alice@example.com"))
        assertEquals(mapOf("target" to JsonValue.Text("alice@example.com"), "purpose" to JsonValue.Text("login")), body)
    }

    @Test
    fun deviceLoginBodies() {
        assertEquals(
            mapOf(
                "deviceName" to JsonValue.Text("Google Pixel 9"),
                "hostId" to JsonValue.Text(""),
                "nextPath" to JsonValue.Text("/account"),
            ),
            parse(AccountAuthRequests.deviceStart("Google Pixel 9")),
        )
        assertEquals(
            mapOf("requestId" to JsonValue.Text("req-1"), "approve" to JsonValue.Bool(true)),
            parse(AccountAuthRequests.deviceDecide("req-1")),
        )
        assertEquals(
            mapOf("requestId" to JsonValue.Text("req-1"), "verifier" to JsonValue.Text("ver-1")),
            parse(AccountAuthRequests.deviceToken("req-1", "ver-1")),
        )
    }

    @Test
    fun oauthStartReturnsToTheAuthorizePage() {
        assertEquals(
            "https://vibex.xforever.xin/api/auth/oauth/github/start?next=%2Fdesktop%2Fauthorize%3Frequest%3Dreq-1",
            AccountAuthRequests.oauthStartUrl(
                "https://vibex.xforever.xin",
                "github",
                "https://vibex.xforever.xin/desktop/authorize?request=req-1",
            ),
        )
    }

    @Test
    fun errorCodesMapToLoginCopy() {
        val cases = mapOf(
            "credentials" to "账号或密码错误",
            "suspended" to "该账号已被停用",
            "banned" to "该账号已被封禁",
            "email_not_found" to "该邮箱尚未注册，请先注册",
            "phone_not_found" to "该手机号尚未注册，请先注册",
            "rate" to "尝试次数过多，请稍后再试",
            "cooldown" to "验证码发送过于频繁，请稍后再试",
            "delivery" to "验证码发送失败，请稍后再试",
            "oauth_state" to "登录超时，请重试",
            "oauth_denied" to "已取消授权",
            "oauth_failed" to "第三方登录失败，请重试",
            "network" to "网络不可用，请重试",
        )
        cases.forEach { (code, message) -> assertEquals(message, AccountAuthErrors.message(code), code) }
    }

    @Test
    fun fieldErrorsMapToLoginCopy() {
        assertEquals("验证码错误或已过期", AccountAuthErrors.message("invalid", listOf("code")))
        for (field in listOf("target", "email", "phone")) {
            assertEquals("格式不正确", AccountAuthErrors.message("invalid", listOf(field)), field)
        }
        assertEquals("密码至少 8 位", AccountAuthErrors.message("invalid", listOf("password")))
        assertEquals("验证码错误或已过期", AccountAuthErrors.message("code"))
    }

    @Test
    fun unknownErrorsFallBack() {
        for (code in listOf(null, "", "invalid", "unauthorized", "http_500", "pending")) {
            assertEquals("登录失败，请重试", AccountAuthErrors.message(code), code.toString())
        }
        assertEquals("登录失败，请重试", AccountAuthErrors.message("invalid", listOf("other")))
    }
}
