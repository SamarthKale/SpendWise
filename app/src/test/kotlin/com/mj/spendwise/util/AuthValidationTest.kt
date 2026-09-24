package com.mj.spendwise.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test
    fun validEmails() {
        assertTrue(AuthValidation.isValidEmail("student@college.edu"))
        assertTrue(AuthValidation.isValidEmail("  a.b+c@mail.co.in  ")) // surrounding spaces are trimmed
    }

    @Test
    fun invalidEmails() {
        assertFalse(AuthValidation.isValidEmail(""))
        assertFalse(AuthValidation.isValidEmail("no-at-sign.com"))
        assertFalse(AuthValidation.isValidEmail("a@b"))
        assertFalse(AuthValidation.isValidEmail("a b@c.com"))
    }

    @Test
    fun problems_areReportedInOrder() {
        assertEquals("Enter your email.", AuthValidation.problem("", "secret1"))
        assertEquals("That email address doesn't look right.", AuthValidation.problem("abc", "secret1"))
        assertEquals("Enter your password.", AuthValidation.problem("a@b.com", ""))
        assertEquals("Password must be at least 6 characters.", AuthValidation.problem("a@b.com", "12345"))
    }

    @Test
    fun confirmPassword_mustMatchWhenRegistering() {
        assertEquals("The two passwords don't match.", AuthValidation.problem("a@b.com", "secret1", "secret2"))
        assertNull(AuthValidation.problem("a@b.com", "secret1", "secret1"))
        assertNull(AuthValidation.problem("a@b.com", "secret1")) // sign-in mode: no confirm field
    }
}
