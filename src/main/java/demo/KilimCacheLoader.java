package demo;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.concurrent.ExecutionException;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

public class KilimCacheLoader<KK,VV> extends CacheLoader<KK,VV> {
    public interface PausableFuture {
        void body(SettableFuture future) throws Pausable;
    }
    public interface Body<KK,VV> {
        VV body(KK key) throws Pausable;
    }

    private static final Object dummy = new Object();
    PausableFuture body;
    public KilimCacheLoader() {}
    public KilimCacheLoader(PausableFuture body) { this.body = body; }

    public VV load(KK key) {
        return (VV) dummy;
    }

    public ListenableFuture reload(KK key,VV oldValue) {
        SettableFuture future = SettableFuture.create();
        Task.fork(() -> {
            if (body==null) body(future);
            else body.body(future);
        });
        return future;
    }

    public void body(SettableFuture future) throws Pausable {}



    public static class Dummy<VV> extends Mailbox<Mailbox<VV>> {}

    /**
     * get a value from the cache asynchronously.
     * this method (or similar logic) must be used for all access.
     * if the key is not available immediately this method pauses until it is ready
     * @param <KK> the key type
     * @param <VV> the value type
     * @param cache the cache to access
     * @param key the key to search for
     * @param delay the number of milliseconds to suspend if the value is not yet available
     * @return
     * @throws Pausable 
     */    
    public static <KK,VV> VV getCache(Cache<KK,VV> cache,Body<KK,VV> body,KK key,int delay) throws Pausable {
        Dummy<VV> d2 = new Dummy();
        Mailbox<VV> mb;
        while (true) {
            try {
                VV result = cache.get(key,() -> ((VV) d2));
                if (result==d2) {
                    VV val = body.body(key);
                    cache.put(key,val);
                    while ((mb = d2.get(0)) != null)
                        mb.put(val);
                }
                else if (result instanceof Dummy) {
                    ((Dummy) result).put(mb = new Mailbox());
                    return mb.get();
                }
                else
                    return result;
            }
            catch (ExecutionException ex) {}
        }
    }

    public static void main(String[] args) throws Exception {
        GuavaCacheDemo.main(args);
    }
}
