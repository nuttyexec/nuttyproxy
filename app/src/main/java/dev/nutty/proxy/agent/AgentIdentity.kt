package dev.nutty.proxy.agent

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.util.Base64
import java.security.MessageDigest

/**
 * Private keys are generated in Android Keystore and never leave the phone.
 * A key is scoped to one trusted gateway + agent id, preventing two unrelated
 * projects from correlating the same phone through a reused public key.
 */
class AgentIdentity {
    fun publicJwk(scope: String): JSONObjectString {
        val alias = alias(scope)
        ensureKey(alias)
        val key = keyStore().getCertificate(alias).publicKey as ECPublicKey
        return JSONObjectString(
            """{"kty":"EC","crv":"P-256","x":"${base64Url(key.w.affineX)}","y":"${base64Url(key.w.affineY)}"}""",
        )
    }

    fun sign(scope: String, payload: ByteArray): ByteArray {
        val alias = alias(scope)
        ensureKey(alias)
        return Signature.getInstance("SHA256withECDSA").apply {
            initSign(keyStore().getKey(alias, null) as java.security.PrivateKey)
            update(payload)
        }.sign()
    }

    /** Drop an identity when the user revokes a server, so re-pairing rotates it. */
    fun remove(scope: String) {
        val store = keyStore()
        if (store.containsAlias(alias(scope))) store.deleteEntry(alias(scope))
    }

    private fun ensureKey(alias: String) {
        if (keyStore().containsAlias(alias)) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generateKeyPair()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun alias(scope: String): String = "nutty_proxy_identity_v1_" +
        MessageDigest.getInstance("SHA-256").digest(scope.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(32)

    private fun base64Url(value: BigInteger): String {
        val raw = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        val padded = ByteArray(32)
        raw.copyInto(padded, 32 - raw.size)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(padded)
    }
}

/** Avoids leaking an org.json dependency into the Keystore boundary. */
@JvmInline value class JSONObjectString(val value: String)
