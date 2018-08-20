# Demo of using Kilim with Guava CacheBuilder


guava provides a CacheLoader, which is used to fill a LoadingCache.
the API supports asynchronous reloading of values, but the initial loading is synchronous.
as a result, non-blocking integration with kilim isn't trivial.
however, by always loading an initial fake value, the synchronous portion can be bypassed

`KilimCacheLoader` extends `CacheLoader` and provides a static getter.




this should also serve as an example of how to integrate other services with kilim



# limitations

the caches are built using the Builder pattern with opaque implementations.
as a result, the getter can't be added to the cache directly,
unfortunately necesitating a static method


