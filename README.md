# kilim guava cache

`KilimCache` is a thin wrapper around a Guava `LoadingCache` to provide fully asynchronous lookup and loading


guava provides a CacheLoader, which is used to fill a LoadingCache.
the API supports asynchronous reloading of values, but the initial loading
and all access during a reload are synchronous.
as a result, non-blocking integration with kilim isn't trivial.

however, by loading placeholder values and then backfilling
when asynchronous retrieval from a backing store completes, the blocking
portion can be avoided




# Limitations

the caches are built using the Builder pattern with opaque implementations.
as a result, the functionality can't be added to the cache directly

# Example Usage

from `GuavaCacheDemo.java`:
```
        KilimCache<Integer,Double> loader = new KilimCache(
                CacheBuilder.newBuilder()
                        .refreshAfterWrite(refresh,TimeUnit.MICROSECONDS)
                        .maximumSize(maxSize));

        loader.register(
                (key,prev) -> {
                    if (key < maxNever & prev != null)
                        return null;
		    return somethingPausable(key);
                });

        Task.fork(() -> {
                double sum = 0;
                for (int ii=1; ii <= numIters; ii++) {
                    int key = random.nextInt(maxKey);
                    sum += loader.get(key) - key;
                }
                System.out.format("cache: %8.3f\n",sum/numIters);
            }
        });
```



# Lessons

this should also serve as an example of how to integrate other services with kilim


