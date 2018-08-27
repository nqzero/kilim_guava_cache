package demo;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.util.concurrent.ExecutionException;
import kilim.Mailbox;
import kilim.Pausable;

public class KilimCacheLoader<KK,VV> {
    public interface Loadable<KK,VV> {
        VV body(KK key) throws Pausable;
    }


    public static class Dummy<VV> extends Mailbox<Mailbox<VV>> {}

    public Loadable<KK,VV> body;
    public LoadingCache<KK,VV> cache;
    
    public KilimCacheLoader(CacheBuilder<KK,VV> builder) {
        cache = builder.build(new CacheLoader() { public Object load(Object arg0) { return null; } });
    }

    public KilimCacheLoader<KK,VV> setReloader(Loadable<KK,VV> body) {
        this.body = body;
        return this;
    }
    

    public VV get(KK key) throws Pausable {
        return getCache(cache,body,key);
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
}
