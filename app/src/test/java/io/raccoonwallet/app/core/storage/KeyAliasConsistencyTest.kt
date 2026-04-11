package io.raccoonwallet.app.core.storage

import org.junit.Assert.*
import org.junit.Test

class KeyAliasConsistencyTest {

    @Test
    fun `IntegrityChecker aliases match KeystoreProvider aliases`() {
        val providerPublic = getPrivateConstant(KeystoreProvider::class.java, "PUBLIC_KEY_ALIAS")
        val providerSecret = getPrivateConstant(KeystoreProvider::class.java, "SECRET_KEY_ALIAS")
        val checkerPublic = getPrivateConstant(IntegrityChecker::class.java, "PUBLIC_KEY_ALIAS")
        val checkerSecret = getPrivateConstant(IntegrityChecker::class.java, "SECRET_KEY_ALIAS")

        assertEquals(
            "PUBLIC_KEY_ALIAS must match between KeystoreProvider and IntegrityChecker",
            providerPublic, checkerPublic
        )
        assertEquals(
            "SECRET_KEY_ALIAS must match between KeystoreProvider and IntegrityChecker",
            providerSecret, checkerSecret
        )
    }

    @Test
    fun `key aliases follow expected naming convention`() {
        val publicAlias = getPrivateConstant(KeystoreProvider::class.java, "PUBLIC_KEY_ALIAS")
        val secretAlias = getPrivateConstant(KeystoreProvider::class.java, "SECRET_KEY_ALIAS")

        assertTrue(
            "Public alias should start with 'raccoonwallet_'",
            publicAlias.startsWith("raccoonwallet_")
        )
        assertTrue(
            "Secret alias should start with 'raccoonwallet_'",
            secretAlias.startsWith("raccoonwallet_")
        )
        assertNotEquals("Public and secret aliases must differ", publicAlias, secretAlias)
    }

    private fun getPrivateConstant(clazz: Class<*>, fieldName: String): String {
        val field = clazz.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(null) as String
    }
}
