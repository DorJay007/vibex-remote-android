package dev.vibex.companion.app

import dev.vibex.companion.AccountLinkException
import dev.vibex.companion.AccountLinkFailure
import dev.vibex.companion.HostUnreachableException
import dev.vibex.companion.PairingException
import java.io.IOException

enum class DesktopConnectRoute { Cloud, Lan }

/** Steps of the account popup that finds the user's desktops and connects one in a single tap. */
sealed interface DesktopConnectStage {
    data object Syncing : DesktopConnectStage

    data object Choosing : DesktopConnectStage

    data class Connecting(val route: DesktopConnectRoute) : DesktopConnectStage

    data class AwaitingApproval(val route: DesktopConnectRoute, val sasCode: String) : DesktopConnectStage

    /** [route] is null when the desktop list itself could not be loaded. */
    data class Failed(val message: String, val route: DesktopConnectRoute?) : DesktopConnectStage
}

data class DesktopConnectSheet(
    val stage: DesktopConnectStage = DesktopConnectStage.Syncing,
    val selectedHostId: String? = null,
)

internal const val DESKTOP_SYNC_FAILED = "同步失败，请重试"
internal const val DESKTOP_APPROVAL_REJECTED = "电脑端已拒绝连接"
internal const val DESKTOP_APPROVAL_EXPIRED = "确认超时，请重试"
private const val ACCOUNT_VERIFICATION_UNAVAILABLE = "暂时无法验证账号，请稍后重试"

private const val HOST_UNREACHABLE = "无法连接到电脑，请确认电脑端在线并已开启远程连接"

internal fun desktopConnectErrorMessage(error: Throwable): String =
    when (error) {
 is AccountLinkException -> when (error.failure) {
     AccountLinkFailure.AccountMismatch -> "这台电脑登录的是其他 VibeX 账号"
     AccountLinkFailure.HostAccountSignedOut -> "这台电脑未登录 VibeX 账号"
     AccountLinkFailure.AccountTokenInvalid,
     AccountLinkFailure.VerificationUnavailable,
     -> ACCOUNT_VERIFICATION_UNAVAILABLE
 }
 is HostUnreachableException, is IOException -> HOST_UNREACHABLE
 is PairingException -> error.message ?: HOST_UNREACHABLE
        else -> error.message ?: HOST_UNREACHABLE
    }
