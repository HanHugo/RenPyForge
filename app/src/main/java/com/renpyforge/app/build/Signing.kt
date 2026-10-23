package com.renpyforge.app.build

import android.content.Context
import com.android.apksig.ApkSigner
import com.android.apksig.util.DataSources
import com.android.apksig.util.DataSinks
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Date

/**
 * 步骤 8：APK 签名（apksig 库，纯 Java，在 ART 上直接运行）。
 *
 * 密钥管理：Android Keystore 的私钥不可导出，与 apksig 需要直接持有
 * PrivateKey 的 API 不匹配，因此使用普通 PKCS12 keystore 文件保存在
 * filesDir 下（文件由 App 沙箱保护）。首次使用时自动生成 RSA-2048 +
 * 自签名证书（BouncyCastle）。
 */
object Signing {

    private const val KS_FILE = "renpyforge.keystore.p12"
    private const val KS_PASS = "renpyforge"
    private const val KS_ALIAS = "renpyforge"

    /** 确保密钥存在；返回已加载的 KeyStore */
    fun ensureKeyStore(context: Context): KeyStore {
        val ksFile = File(context.filesDir, KS_FILE)
        if (!ksFile.isFile) {
            generateKeystore(ksFile)
        }
        val ks = KeyStore.getInstance("PKCS12")
        ksFile.inputStream().use { ks.load(it, KS_PASS.toCharArray()) }
        return ks
    }

    private fun generateKeystore(target: File) {
        val kpg = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }
        val kp = kpg.generateKeyPair()

        val now = Date()
        val builder = JcaX509v3CertificateBuilder(
            X500Name("CN=RenPyForge Debug,O=RenPyForge,C=US"),
            BigInteger(63, SecureRandom()),          // serial
            now,
            Date(now.time + 365L * 24 * 3600 * 1000 * 25), // 25 年
            X500Name("CN=RenPyForge Debug,O=RenPyForge,C=US"),
            kp.public,
        ).addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(KS_ALIAS, kp.private, KS_PASS.toCharArray(), arrayOf(cert))
        target.outputStream().use { ks.store(it, KS_PASS.toCharArray()) }
    }

    /** 对已 zipalign 的 APK 做签名，产出已签名 APK */
    fun sign(input: File, output: File, keystore: KeyStore) {
        val privateKey = keystore.getKey(KS_ALIAS, KS_PASS.toCharArray()) as RSAPrivateKey
        val certChain = keystore.getCertificateChain(KS_ALIAS)
            ?.map { it as X509Certificate }
            ?.toTypedArray()
            ?: throw StepException(StepId.SIGN, "keystore 中未找到证书链")

        val signerConfig = ApkSigner.SignerConfig.Builder(
            "renpyforge", privateKey, listOf(*certChain)
        ).build()

        output.delete()
        val inputRaf = java.io.RandomAccessFile(input, "r")
        inputRaf.use {
            ApkSigner.Builder(listOf(signerConfig))
                .setMinSdkVersion(26)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setOtherSignersSignaturesPreserved(false)
                .setInputApk(DataSources.asDataSource(inputRaf))
                .setOutputApk(output)
                .build()
                .sign()
        }
        if (!output.isFile || output.length() == 0L) {
            throw StepException(StepId.SIGN, "签名后未产出 APK")
        }
    }
}
