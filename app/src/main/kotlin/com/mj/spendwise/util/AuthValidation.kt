package com.mj.spendwise.util

/** Client-side checks for the login form (Firebase still has the final say). Pure Kotlin, unit-tested. */
object AuthValidation {
    // Deliberately simple: something@something.tld, no spaces.
    private val emailRegex = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]{2,}$""")

    /** Firebase requires at least 6 characters. */
    const val MIN_PASSWORD = 6

    fun isValidEmail(email: String): Boolean = emailRegex.matches(email.trim())

    /** Returns a message describing the first problem, or null when everything is fine. */
    fun problem(email: String, password: String, confirm: String? = null): String? = when {
        email.isBlank() -> "Enter your email."
        !isValidEmail(email) -> "That email address doesn't look right."
        password.isEmpty() -> "Enter your password."
        password.length < MIN_PASSWORD -> "Password must be at least $MIN_PASSWORD characters."
        confirm != null && confirm != password -> "The two passwords don't match."
        else -> null
    }
}
