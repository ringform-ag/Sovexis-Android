package com.sovexis.mobile.core.utils

import android.util.Patterns

object Validators {

    fun isValidDid(did: String): Boolean {
        return did.matches(Regex("^did:sovexis:0x[0-9a-fA-F]{64}$"))
    }

    fun isValidAlias(alias: String): Boolean {
        return alias.matches(Regex("^[a-zA-Z0-9_-]{3,32}$"))
    }

    fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun isValidHex64(hex: String): Boolean {
        return hex.matches(Regex("^[0-9a-fA-F]{64}$"))
    }

    fun isNotEmpty(text: String): Boolean = text.isNotBlank()
}
