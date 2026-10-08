package com.routerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class CloudRulesTest {
    private val id = "00000000-0000-0000-0000-000000000001"
    private val name = "backup-20261008T153045-00000000-0000-0000-0000-000000000002.rrb"

    private fun legacy(role: String): String {
        fun enc(s: String) = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(s.toByteArray())
        return enc("""{"alg":"HS256","typ":"JWT"}""") + "." +
            enc("""{"role":"$role","iat":1660000000}""") + ".fake-signature"
    }

    @Test fun rejectsInvalidAndPrivilegedSupabaseConnections() {
        val pub = "sb_publishable_" + "a".repeat(36)
        assertTrue(CloudSettings("https://abcd1234.supabase.co", pub).valid())
        assertTrue(CloudSettings("https://abcd1234.supabase.co/", pub).valid())
        assertFalse(CloudSettings("http://abcd1234.supabase.co", pub).valid())
        assertFalse(CloudSettings("https://attacker.example.com", pub).valid())
        assertFalse(CloudSettings("https://abcd1234.supabase.co.evil.com", pub).valid())
        assertFalse(CloudSettings("https://abcd1234.supabase.co", "sb_secret_" + "a".repeat(40)).valid())
        assertFalse(CloudSettings("https://abcd1234.supabase.co", legacy("service_role")).valid())
        assertTrue(CloudSettings("https://abcd1234.supabase.co", legacy("anon")).valid())
    }

    @Test fun cloudPathsCannotTraverseOrAccessOtherFiles() {
        assertEquals(id + "/" + name, CloudRules.path(id, name))
        assertTrue(CloudRules.validName(CloudRules.newName()))
        assertThrows(IllegalArgumentException::class.java) {
            CloudRules.path(id, "../backup-other.rrb")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CloudRules.path("../someone-else", name)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CloudRules.path(id, "invoice.pdf")
        }
    }

    @Test fun pathsAreBoundToOneUserAndDoNotAcceptSlash() {
        assertFalse(CloudRules.validUser(id + "/evil"))
        assertFalse(CloudRules.validName("folder/" + name))
        assertTrue(CloudRules.MAX_CLOUD_BYTES >= 150L * 1024L * 1024L)
    }
}
