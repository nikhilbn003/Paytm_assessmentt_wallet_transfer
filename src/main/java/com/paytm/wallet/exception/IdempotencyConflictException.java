package com.paytm.wallet.exception;

/**
 * Same idempotency key reused with a different request body -&gt; 409, per spec.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String idempotencyKey) {
        super("idempotency_key '" + idempotencyKey + "' was already used with a different request body");
    }
}
