package com.mj.spendwise.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProfileKeysTest {
    @Test
    fun guests_shareOneFile() {
        // Every guest login (each is a different anonymous cloud user) maps to the same pre-filled file.
        assertEquals(ProfileKeys.GUEST, ProfileKeys.keyFor("anon-uid-1", true))
        assertEquals(ProfileKeys.GUEST, ProfileKeys.keyFor("anon-uid-2", true))
        assertEquals("spendwise_guest.db", ProfileKeys.dbFileName(ProfileKeys.GUEST))
    }

    @Test
    fun accounts_getTheirOwnFile() {
        val a = ProfileKeys.dbFileName(ProfileKeys.keyFor("uidAAA111", false))
        val b = ProfileKeys.dbFileName(ProfileKeys.keyFor("uidBBB222", false))
        assertEquals("spendwise_u_uidAAA111.db", a)
        assertNotEquals(a, b) // two logins never share a database
        assertNotEquals("spendwise_guest.db", a)
    }

    @Test
    fun fileNames_areSafe() {
        // Anything that could climb out of the databases folder is neutralised.
        assertEquals("spendwise_u____evil_.db", ProfileKeys.dbFileName("../evil/"))
    }
}
