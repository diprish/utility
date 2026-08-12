package com.diprish.utilitymeter.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

/** Outcome of requesting Drive authorization. */
sealed interface AuthOutcome {
    /** Authorized — carries a short-lived OAuth access token for Drive REST calls. */
    data class Authorized(val token: String) : AuthOutcome

    /** First-time (or re-)consent required — launch [pendingIntent] for the user. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : AuthOutcome

    data class Error(val message: String) : AuthOutcome
}

/**
 * Thin wrapper over the Play Services Authorization API. Requests the
 * least-privilege `drive.file` scope (only files this app creates), so no
 * Google app-verification is required for personal use. Once granted, tokens
 * can be fetched silently — which is what lets the background worker back up
 * without prompting.
 */
class GoogleAuthManager(context: Context) {

    private val client = Identity.getAuthorizationClient(context.applicationContext)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .build()

    /** Silent when already granted; otherwise returns a consent [PendingIntent]. */
    suspend fun authorize(): AuthOutcome = try {
        toOutcome(client.authorize(request).await())
    } catch (t: Throwable) {
        AuthOutcome.Error(t.message ?: "Authorization failed")
    }

    /** Resolve the result returned from launching the consent intent. */
    fun resultFromIntent(intent: Intent): AuthOutcome = try {
        toOutcome(client.getAuthorizationResultFromIntent(intent))
    } catch (t: Throwable) {
        AuthOutcome.Error(t.message ?: "Authorization failed")
    }

    private fun toOutcome(result: AuthorizationResult): AuthOutcome =
        if (result.hasResolution()) {
            result.pendingIntent?.let { AuthOutcome.NeedsConsent(it) }
                ?: AuthOutcome.Error("Consent required but no intent provided")
        } else {
            result.accessToken?.let { AuthOutcome.Authorized(it) }
                ?: AuthOutcome.Error("No access token returned")
        }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
