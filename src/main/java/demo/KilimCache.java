package demo;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

public class KilimCache<KK,VV> {
    public interface Loadable<KK,VV> {
        VV body(KK key,VV prev) throws Pausable;
    }


    public static class Dummy<VV> extends Mailbox<Mailbox<VV>> {}


    // guava logs null values - make it clear that this is intentional
    public static class HarmlessException extends Exception {}

    public Loadable<KK,VV> loader;
    public LoadingCache<KK,VV> guava;
    
    public KilimCache(CacheBuilder<KK,VV> builder) {
        guava = builder.build(new MyLoader());
    }

    private class MyLoader extends CacheLoader<KK,VV> {
        public VV load(KK key) throws Exception {
            Dummy<VV> d2 = new Dummy();
            Task.fork(() -> send(guava,loader,key,null,d2));
            return (VV) d2;
        }
        public ListenableFuture reload(Object key,Object prev) {
            return Futures.immediateFuture(prev);
        }
    }

    
    
    public KilimCache<KK,VV> set(Loadable<KK,VV> body) {
        this.loader = body;
        return this;
    }

    private static void impossible(Throwable ex) {
        throw new RuntimeException("this should never happen",ex);
    }

    public VV get(KK key) throws Pausable {
        return getCache(guava,loader,key);
    }    
    
    /**
     * get a value from the cache asynchronously.this method (or similar logic) must be used for all access.
     * if the key is not available immediately this method pauses until it is ready
     * @param <KK> the key type
     * @param <VV> the value type
     * @param cache the cache to access
     * @param body lambda that returns the value associated with a key
     * @param key the key to search for
     * @return
     * @throws Pausable 
     */
    public static <KK,VV> VV getCache(Cache<KK,VV> cache,Loadable<KK,VV> body,KK key) throws Pausable {
        Dummy<VV> d2 = new Dummy();
        Mailbox<VV> mb;
        VV result = null;
        VV prev = cache.getIfPresent(key);
        if (prev instanceof Dummy)
            result = prev;
        else
            try { result = cache.get(key,() -> ((VV) d2)); }
            catch (ExecutionException ex) { impossible(ex); }

        if (result==d2)
            return send(cache,body,key,prev,d2);
        else if (result instanceof Dummy) {
            ((Dummy) result).put(mb = new Mailbox());
            return mb.get();
        }
        else
            return result;
    }

    private static
        <VV,KK> VV send(Cache<KK,VV> cache,Loadable<KK,VV> body,KK key,VV prev,Dummy<VV> d2)
            throws Pausable {
        VV val = body.body(key,prev);
        cache.put(key,val);
        for (Mailbox<VV> mb; (mb = d2.get(0)) != null; )
            mb.put(val);
        return val;
    }
}
