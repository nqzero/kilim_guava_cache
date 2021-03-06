package kilim.guava;

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
 * the underlying cache is exposed, but the various getters should not be used.
 * all lookup must be through this class, ie {@code get}.
 * {@code register} must be called before the cache can be used
 * @param <KK> the key type
 * @param <VV> the value type
 */
public class KilimCache<KK,VV> {
    public interface Reloadable<KK,VV> {
        /**
         * Computes or fetches a (potentially replacement) value
         * corresponding to a (potentially already-cached) key.
         * @param key the key
         * @param prev the previous value, either of type VV or {@code Relay}
         * @return the new value, or null to reuse the old value
         */
        VV reload(KK key,Object prev) throws Pausable;
    }

    public Reloadable<KK,VV> reloader;
    public LoadingCache<KK,VV> guava;

    /**
     * create a new cache - a reloader must be {@code register}ed before the cache can be used
     * @param builder the builder to use to create the backing store
     */    
    public KilimCache(CacheBuilder<KK,VV> builder) {
        guava = builder.build(new MyLoader());
    }
    /**
     * register a pausable reloader - must be called before the cache can be used
     * @param reloader the reloader that computes or fetches values
     * @return the cache
     */
    public KilimCache<KK,VV> register(Reloadable<KK,VV> reloader) {
        this.reloader = reloader;
        return this;
    }
    private class MyLoader extends CacheLoader<KK,VV> {
        public VV load(KK key) throws Exception {
            return (VV) fork(key,null);
        }
        Relay<VV> fork(KK key,VV prev) {
            Relay<VV> relay = new Relay();
            Task.fork(() -> send(key,prev,relay));
            return relay;
        }
        public ListenableFuture reload(KK key,VV prev) {
            return Futures.immediateFuture(fork(key,prev));
        }
    }
    /** a marker class used to represent a value that is not yet available */
    public static class Relay<VV> {
        private Mailbox<Mailbox<VV>> box = new Mailbox();
        private volatile VV dead;
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
        else if (result instanceof Relay)
            return chain((Relay<VV>) result);
        else
            return result;
    }

    private VV send(KK key,VV prev,Relay<VV> relay) throws Pausable {
        VV val = reloader.reload(key,prev);
        if (val==null)
            val = prev instanceof Relay ? chain((Relay<VV>) prev):prev;
        else
            guava.put(key,val);
        synchronized (relay) {
            relay.dead = val;
        }
        for (Mailbox<VV> mb; (mb = relay.box.get(0)) != null; )
            mb.put(val);
        return val;
    }

    private VV chain(Relay<VV> master) throws Pausable {
        Mailbox<VV> mb = new Mailbox();
        while (true) {
            synchronized (master) {
                if (master.dead != null)
                    return master.dead;
                if (master.box.putnb(mb))
                    break;
            }
            Task.sleep(0);
        }
        return mb.get();
    }
}
