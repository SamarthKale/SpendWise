package com.spendwise.backend;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Anonymous sign-in: zero-friction "login" for the demo. The uid scopes all Firestore data. */
public class AuthManager {

    private AuthManager() { }

    /** Reuses the existing user if there is one (works offline), otherwise signs in anonymously. */
    public static void signInAnonymouslyIfNeeded(Callback<String> onUid, Callback<Exception> onError) {
        try {
            FirebaseAuth auth = FirebaseAuth.getInstance();
            FirebaseUser current = auth.getCurrentUser();
            if (current != null) {
                onUid.onResult(current.getUid());
                return;
            }
            auth.signInAnonymously()
                    .addOnSuccessListener(result -> {
                        FirebaseUser user = result.getUser();
                        if (user != null) onUid.onResult(user.getUid());
                        else onError.onResult(new IllegalStateException("Sign-in returned no user"));
                    })
                    .addOnFailureListener(onError::onResult);
        } catch (Exception e) {
            onError.onResult(e);
        }
    }
}
