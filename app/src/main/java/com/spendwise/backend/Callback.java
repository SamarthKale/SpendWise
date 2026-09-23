package com.spendwise.backend;

/** Tiny callback type so Kotlin can pass lambdas into the Java backend. */
public interface Callback<T> {
    void onResult(T value);
}
