package com.novamind.app.common.net

import java.net.HttpURLConnection
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection

/**
 * 接口测试相关的 TLS 主机名钉定。
 *
 * 接口服务器证书无 SAN、仅 CN 含 IP，默认主机名校验会失败。
 * 这里只对该主机放宽“主机名绑定”：仍要求服务器证书指纹与钉定值一致，
 * 证书链校验依旧由 network_security_config 完整执行（非 trust-all）。
 *
 * GET（[ApiTestViewModel]）与图片上传（ImageUploadViewModel）共用此逻辑。
 */
internal object ApiTls {

    /** 需要放宽主机名校验的目标主机。 */
    private const val PINNED_HOST = "121.41.207.114"

    /** 钉定的服务器证书 SHA-256 指纹（大写十六进制，无分隔符）。 */
    private const val PINNED_CERT_SHA256 =
        "23C623C3BC4A8C7B364AA539115BC063A4C2ECA90D86768631DF6C084C6A7B35"

    /**
     * 仅对 [PINNED_HOST] 放宽主机名绑定：校验对端证书指纹与 [PINNED_CERT_SHA256] 一致即放行。
     * 其它主机一律走系统默认校验。指纹不匹配则拒绝。
     */
    /** 钉定主机名校验器：对 [PINNED_HOST] 校验证书指纹，其它主机走系统默认。供 OkHttp / HttpURLConnection 复用。 */
    val PINNED_HOSTNAME_VERIFIER = HostnameVerifier { hostname, session ->
        if (hostname == PINNED_HOST) {
            runCatching {
                val cert = session.peerCertificates.firstOrNull() as? X509Certificate
                cert != null && sha256Hex(cert.encoded) == PINNED_CERT_SHA256
            }.getOrDefault(false)
        } else {
            HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
        }
    }

    /** 若为 HTTPS 连接则套用钉定主机名校验；HTTP 连接无影响。 */
    fun apply(conn: HttpURLConnection) {
        if (conn is HttpsURLConnection) conn.hostnameVerifier = PINNED_HOSTNAME_VERIFIER
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02X".format(it) }
}
