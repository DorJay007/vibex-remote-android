package dev.vibex.companion

import java.net.URI
import java.net.URLEncoder

/** Request bodies for the VibeX site's in-app account login endpoints. */
object AccountAuthRequests {
    fun passwordLogin(login: String, password: String): String =
        encodeObject(mapOf("mode" to "password", "login" to login.trim(), "password" to password))

    fun codeLogin(login: String, code: String): String =
        encodeObject(mapOf("mode" to "code", "login" to login.trim(), "code" to code.trim()))

    fun sendLoginCode(target: String): String =
        encodeObject(mapOf("target" to target.trim(), "purpose" to "login"))

    fun deviceStart(deviceName: String): String =
        encodeObject(mapOf("deviceName" to deviceName, "hostId" to "", "nextPath" to "/account"))

    fun deviceDecide(requestId: String): String =
        encodeObject(mapOf("requestId" to requestId, "approve" to true))

    fun deviceToken(requestId: String, verifier: String): String =
        encodeObject(mapOf("requestId" to requestId, "verifier" to verifier))

    /** Provider sign-in page that returns to the device login's authorize page once the site session exists. */
    fun oauthStartUrl(siteBaseUrl: String, provider: String, verifyUrl: String): String {
        val verify = URI(verifyUrl)
        val next = verify.rawPath + (verify.rawQuery?.let { "?$it" } ?: "")
        return "$siteBaseUrl/api/auth/oauth/$provider/start?next=${URLEncoder.encode(next, "UTF-8")}"
    }
}

/** Maps the site's `{error, fields}` login failures to the copy shown on the login screen. */
object AccountAuthErrors {
    const val FALLBACK = "登录失败，请重试"

    private val byCode = mapOf(
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

    private val byField = mapOf(
        "code" to "验证码错误或已过期",
        "target" to "格式不正确",
        "email" to "格式不正确",
        "phone" to "格式不正确",
        "password" to "密码至少 8 位",
    )

    fun message(code: String?, fields: List<String> = emptyList()): String =
        byCode[code]
            ?: fields.firstNotNullOfOrNull { byField[it] }
            ?: byField[code]
            ?: FALLBACK
}
