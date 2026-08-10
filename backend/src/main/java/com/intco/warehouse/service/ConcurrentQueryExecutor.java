package com.intco.warehouse.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ConcurrentQueryExecutor {
    private final Executor executor;

    public ConcurrentQueryExecutor(@Qualifier("warehouseQueryExecutor") Executor executor) {
        this.executor = executor;
    }

    public <T> CompletableFuture<T> submit(Supplier<T> query) {
        return CompletableFuture.supplyAsync(query, executor);
    }

    public void awaitAll(CompletableFuture<?>... queries) {
        try {
            CompletableFuture.allOf(queries).join();
        } catch (CompletionException error) {
            rethrow(error);
        }
    }

    public <T> T await(CompletableFuture<T> query) {
        try {
            return query.join();
        } catch (CompletionException error) {
            return rethrow(error);
        }
    }

    private static <T> T rethrow(CompletionException error) {
        Throwable cause = error.getCause();
        if (cause instanceof RuntimeException) throw (RuntimeException) cause;
        if (cause instanceof Error) throw (Error) cause;
        throw new IllegalStateException("Concurrent query failed", cause);
    }
}
