package com.mj.spendwise.backend;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;

/**
 * All Firebase Authentication calls live here.
 *  - Email + password accounts (sign in / register / password reset)
 *  - "Continue as guest" = Firebase Anonymous Auth (zero-friction demo login)
 *  - A guest can later attach an email + password to the SAME uid ({@link #linkEmail}), so no data is lost.
 * Every method reports back through callbacks and never throws.
 */
public class AuthManager {

    private AuthManager() { }

    // ---------------- current user ----------------

    /** uid of the signed-in user, or null when signed out (or Firebase is not configured). */
    public static String currentUid() {
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            return u == null ? null : u.getUid();
        } catch (Exception e) {
            return null;
        }
    }

    /** Email of a real account; null for guests and when signed out. */
    public static String currentEmail() {
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            String email = u == null ? null : u.getEmail();
            return (email == null || email.isEmpty()) ? null : email;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isGuest() {
        try {
            FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            return u != null && u.isAnonymous();
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- sign in / up ----------------

    public static void signIn(String email, String password, Callback<String> onUid, Callback<Exception> onError) {
        try {
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(r -> deliverUid(r.getUser(), onUid, onError))
                    .addOnFailureListener(onError::onResult);
        } catch (Exception e) {
            onError.onResult(e);
        }
    }

    public static void register(String email, String password, Callback<String> onUid, Callback<Exception> onError) {
        try {
            FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(r -> deliverUid(r.getUser(), onUid, onError))
                    .addOnFailureListener(onError::onResult);
        } catch (Exception e) {
            onError.onResult(e);
        }
    }

    /** Anonymous sign-in; reuses the current user if there already is one. */
    public static void signInAsGuest(Callback<String> onUid, Callback<Exception> onError) {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser current = auth.getCurrentUser();
            if (current != null) {
                onUid.onResult(current.getUid());
                return;
            }
            auth.signInAnonymously()
                    .addOnSuccessListener(r -> deliverUid(r.getUser(), onUid, onError))
                    .addOnFailureListener(onError::onResult);
        } catch (Exception e) {
            onError.onResult(e);
        }
    }

    /** Turns the current guest into a real account. Same uid, so all of the guest's data is kept. */
    public static void linkEmail(String email, String password, Callback<String> onUid, Callback<Exception> onError) {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                onError.onResult(new IllegalStateException("Not signed in"));
                return;
            }
            AuthCredential credential = EmailAuthProvider.getCredential(email, password);
            user.linkWithCredential(credential)
                    .addOnSuccessListener(r -> deliverUid(r.getUser(), onUid, onError))
                    .addOnFailureListener(onError::onResult);
        } catch (Exception e) {
            onError.onResult(e);
        }
    }

    public static void sendPasswordReset(String email, Callback<Boolean> onDone, Callback<Exception> onError) {
        try {
            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnSuccessListener(v -> onDone.onResult(true))
                    .addOnFailureListener(onError::onResult);
        } catch (Exception e) {
            onError.onResult(e);
        }
    }

    public static void signOut() {
        try {
            FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) {
            // Nothing to sign out of (Firebase not configured).
        }
    }

    // ---------------- helpers ----------------

    private static void deliverUid(FirebaseUser user, Callback<String> onUid, Callback<Exception> onError) {
        if (user != null) onUid.onResult(user.getUid());
        else onError.onResult(new IllegalStateException("Sign-in returned no user"));
    }

    /** Turns a Firebase exception into a sentence a user can act on. */
    public static String describe(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return "No internet connection. Check your network and try again.";
        }
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            switch (code) {
                case "ERROR_INVALID_EMAIL":
                    return "That email address doesn't look right.";
                case "ERROR_WRONG_PASSWORD":
                case "ERROR_INVALID_CREDENTIAL":
                case "ERROR_USER_NOT_FOUND":
                    return "Wrong email or password.";
                case "ERROR_USER_DISABLED":
                    return "This account has been disabled.";
                case "ERROR_EMAIL_ALREADY_IN_USE":
                case "ERROR_CREDENTIAL_ALREADY_IN_USE":
                    return "An account with this email already exists. Try signing in instead.";
                case "ERROR_WEAK_PASSWORD":
                    return "Password is too weak. Use at least 6 characters.";
                case "ERROR_TOO_MANY_REQUESTS":
                    return "Too many attempts. Please wait a bit and try again.";
                case "ERROR_OPERATION_NOT_ALLOWED":
                    return "This sign-in method is not enabled. Turn on Email/Password (or Anonymous) under "
                            + "Firebase console > Authentication > Sign-in method.";
                default:
                    break;
            }
        }
        String message = e.getMessage();
        return (message == null || message.isEmpty()) ? "Something went wrong. Please try again." : message;
    }
}
