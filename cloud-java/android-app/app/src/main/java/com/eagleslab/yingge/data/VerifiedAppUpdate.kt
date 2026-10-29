package com.eagleslab.yingge.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.eagleslab.yingge.data.contract.MobileAppReleaseDto
import okhttp3.ResponseBody
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class VerifiedAppUpdate(val releaseId: String, val versionName: String, val file: File, val sha256: String)

/** Downloads into app-private storage and fails closed unless hash and signer match. */
internal class AppUpdateVerifier(private val context: Context) {
    fun verify(release: MobileAppReleaseDto, body: ResponseBody): VerifiedAppUpdate {
        val expectedHash = release.apkSha256?.lowercase()
            ?: throw RepositoryException("UPDATE_HASH_MISSING", "服务器未提供 APK SHA-256，已拒绝下载")
        val directory = File(context.filesDir, "verified-updates").apply { mkdirs() }
        val pending = File(directory, "${release.id}.apk.pending")
        val ready = File(directory, "${release.id}.apk")
        try {
            pending.outputStream().use { output -> body.byteStream().use { input -> input.copyTo(output) } }
            release.apkSize?.let { expected ->
                if (pending.length() != expected) throw RepositoryException("UPDATE_SIZE_MISMATCH", "更新包大小校验失败")
            }
            val actualHash = sha256(pending)
            if (!actualHash.equals(expectedHash, ignoreCase = true)) throw RepositoryException("UPDATE_HASH_MISMATCH", "更新包 SHA-256 校验失败")
            verifySigningCertificate(pending)
            if (ready.exists()) ready.delete()
            if (!pending.renameTo(ready)) throw RepositoryException("UPDATE_STORE_FAILED", "更新包无法写入安全目录")
            return VerifiedAppUpdate(release.id, release.versionName, ready, actualHash)
        } catch (error: RepositoryException) {
            throw error
        } catch (_: IOException) {
            throw RepositoryException("UPDATE_DOWNLOAD_INTERRUPTED", "更新包下载中断，请检查网络后重试")
        } catch (_: SecurityException) {
            throw RepositoryException("UPDATE_VERIFY_FORBIDDEN", "系统拒绝校验更新包，请检查应用存储权限")
        } catch (_: RuntimeException) {
            throw RepositoryException("UPDATE_VERIFY_FAILED", "更新包校验失败，请重新下载")
        } catch (error: Throwable) {
            throw RepositoryException("UPDATE_VERIFY_FAILED", "更新包校验失败，请重新下载")
        } finally {
            if (pending.exists()) pending.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifySigningCertificate(apk: File) {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: throw RepositoryException("UPDATE_APK_INVALID", "下载内容不是有效 APK")
        val current = context.packageManager.getPackageInfo(context.packageName, flags)
        val archiveSigners = archive.signingInfo?.apkContentsSigners.orEmpty().map { sha256(it.toByteArray()) }.toSet()
        val currentSigners = current.signingInfo?.apkContentsSigners.orEmpty().map { sha256(it.toByteArray()) }.toSet()
        if (archiveSigners.isEmpty() || archiveSigners != currentSigners) throw RepositoryException("UPDATE_SIGNER_MISMATCH", "更新包签名与当前应用不一致")
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
