package tokyo.peya.obfuscator.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

@Slf4j
public class ParallelExecutor
{
    public static <T> List<T> runInParallel(int threads, Supplier<Callable<T>> taskSupplier) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<T>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(taskSupplier.get()));
        }

        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                log.error("Error executing task in parallel", e);
            }
        }

        executor.shutdown();
        return results;
    }

    public static <K, V> Map<K, V> runInParallelAndMerge(int threads, Supplier<Callable<Map<K, V>>> taskSupplier) {
        List<Map<K, V>> resultList = runInParallel(threads, taskSupplier);
        Map<K, V> merged = new HashMap<>();
        for (Map<K, V> result : resultList) {
            if (result != null) {
                merged.putAll(result);
            }
        }
        return merged;
    }
}
