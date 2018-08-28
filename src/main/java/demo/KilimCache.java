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
    public interface Reloadable<KK,VV> {
        VV body(KK key,VV prev) throws Pausable;
    }


    public static class Relay<VV> extends Mailbox<Mailbox<VV>> {
        volatile boolean dead;
    }


    // guava logs null values - make it clear that this is intentional
    public static class HarmlessException extends Exception {}

    public Reloadable<KK,VV> reloader;
    public LoadingCache<KK,VV> guava;
    
    public KilimCache(CacheBuilder<KK,VV> builder) {
        guava = builder.build(new MyLoader());
    }

    private class MyLoader extends CacheLoader<KK,VV> {
        public VV load(KK key) throws Exception {
            Relay<VV> relay = new Relay();
            Task.fork(() -> send(guava,reloader,key,null,relay));
            return (VV) relay;
        }
        public ListenableFuture reload(Object key,Object prev) {
            return Futures.immediateFuture(prev);
        }
    }

    
    
    public KilimCache<KK,VV> set(Reloadable<KK,VV> reloader) {
        this.reloader = reloader;
        return this;
    }

    private static void impossible(Throwable ex) {
        throw new RuntimeException("this should never happen",ex);
    }

    public VV get(KK key) throws Pausable {
        return getCache(guava,reloader,key);
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
    public static <KK,VV> VV getCache(Cache<KK,VV> cache,Reloadable<KK,VV> body,KK key) throws Pausable {
        Relay<VV> relay = new Relay();
        cache:
        while (true) {
            VV result = null;
            VV prev = cache.getIfPresent(key);
            if (prev instanceof Relay)
                result = prev;
            else
                try { result = cache.get(key,() -> ((VV) relay)); }
                catch (ExecutionException ex) { impossible(ex); }

            if (result==relay)
                return send(cache,body,key,prev,relay);
            else if (result instanceof Relay) {
                Mailbox<VV> mb = new Mailbox();
                Relay master = (Relay) result;
                while (true) {
                    synchronized (master) {
                        if (master.dead)
                            continue cache;
                        if (master.putnb(mb))
                            break;
                    }
                    Task.sleep(0);
                }
                return mb.get();
            }
            else
                return result;
        }
    }

    private static
        <VV,KK> VV send(Cache<KK,VV> cache,Reloadable<KK,VV> body,KK key,VV prev,Relay<VV> relay)
            throws Pausable {
        VV val = body.body(key,prev);
        cache.put(key,val);
        synchronized (relay) {
            relay.dead = true;
        }
        for (Mailbox<VV> mb; (mb = relay.get(0)) != null; )
            mb.put(val);
        return val;
    }
}
