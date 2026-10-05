package es.carlospb.modoguardia

import es.carlospb.modoguardia.domain.UpdatePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    @Test
    fun `only a higher versionCode is an update`() {
        assertTrue(UpdatePolicy.isNewer(installedVersionCode = 1, publishedVersionCode = 2))
        assertFalse(UpdatePolicy.isNewer(installedVersionCode = 2, publishedVersionCode = 2))
        assertFalse(UpdatePolicy.isNewer(installedVersionCode = 3, publishedVersionCode = 2))
    }

    @Test
    fun `apk from carlospb over https is trusted`() {
        assertTrue(UpdatePolicy.isTrustedApkUrl("https://www.carlospb.es/ModoGuardia-1.1.0.apk"))
        assertTrue(UpdatePolicy.isTrustedApkUrl("https://carlospb.es/ModoGuardia.apk"))
        assertTrue(UpdatePolicy.isTrustedApkUrl("https://WWW.CARLOSPB.ES/ModoGuardia.apk?v=2"))
    }

    @Test
    fun `other hosts, plain http and non apk paths are rejected`() {
        assertFalse(UpdatePolicy.isTrustedApkUrl(null))
        assertFalse(UpdatePolicy.isTrustedApkUrl(""))
        assertFalse(UpdatePolicy.isTrustedApkUrl("http://www.carlospb.es/ModoGuardia.apk"))
        assertFalse(UpdatePolicy.isTrustedApkUrl("https://evil.example/ModoGuardia.apk"))
        assertFalse(UpdatePolicy.isTrustedApkUrl("https://www.carlospb.es.evil.example/ModoGuardia.apk"))
        assertFalse(UpdatePolicy.isTrustedApkUrl("https://www.carlospb.es@evil.example/ModoGuardia.apk"))
        assertFalse(UpdatePolicy.isTrustedApkUrl("https://www.carlospb.es/index.html"))
    }

    @Test
    fun `untrusted url falls back to the official download`() {
        assertEquals(UpdatePolicy.FALLBACK_APK_URL, UpdatePolicy.apkUrlOrFallback("https://evil.example/x.apk"))
        assertEquals(
            "https://www.carlospb.es/ModoGuardia-1.1.0.apk",
            UpdatePolicy.apkUrlOrFallback("https://www.carlospb.es/ModoGuardia-1.1.0.apk"),
        )
    }
}
