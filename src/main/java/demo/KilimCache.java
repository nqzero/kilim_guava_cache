package demo;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

/**
 * a wrapper around the guava {@code LoadingCache} to allow fully async lookup.
 * the underlying cache is exposed
 * @param <KK> the key type
 * @param <VV> the value type
 */
public class KilimCache<KK,VV> {
    public interface Reloadable<KK,VV> {
        VV body(KK key,VV prev) throws Pausable;
    }

    public Reloadable<KK,VV> reloader;
    public LoadingCache<KK,VV> guava;
    
    public KilimCache(CacheBuilder<KK,VV> builder) {
        guava = builder.build(new MyLoader());
    }
    public KilimCache<KK,VV> register(Reloadable<KK,VV> reloader) {
        this.reloader = reloader;
        return this;
    }
    private class MyLoader extends CacheLoader<KK,VV> {
        public VV load(KK key) throws Exception {
            Relay<VV> relay = new Relay();
            Task.fork(() -> send(key,null,relay));
            return (VV) relay;
        }
        public ListenableFuture reload(Object key,Object prev) {
            return Futures.immediateFuture(prev);
        }
    }
    public static class Relay<VV> extends Mailbox<Mailbox<VV>> {
        public VV dead;
    }
    private static void impossible(Throwable ex) {
        throw new RuntimeException("this should never happen",ex);
    }

    /**
     * get a value from the cache asynchronously.this method (or similar logic) must be used for all access.
     * if the key is not available immediately this method pauses until it is ready
     * @param key the key to lookup
     * @return the cached value
     * @throws Pausable 
     */
    public VV get(KK key) throws Pausable {
        Relay<VV> relay = new Relay();
        VV result = null;
        VV prev = guava.getIfPresent(key);
        if (prev instanceof Relay)
            result = prev;
        else
            try { result = guava.get(key,() -> ((VV) relay)); }
            catch (ExecutionException ex) { impossible(ex); }

        if (result==relay)
            return send(key,prev,relay);
        else if (result instanceof Relay) {
            Mailbox<VV> mb = new Mailbox();
            Relay<VV> master = (Relay) result;
            while (true) {
                synchronized (master) {
                    if (master.dead != null)
                        return master.dead;
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

    private VV send(KK key,VV prev,Relay<VV> relay) throws Pausable {
        VV val = reloader.body(key,prev);
        guava.put(key,val);
        synchronized (relay) {
            relay.dead = val;
        }
        for (Mailbox<VV> mb; (mb = relay.get(0)) != null; )
            mb.put(val);
        return val;
    }
}
