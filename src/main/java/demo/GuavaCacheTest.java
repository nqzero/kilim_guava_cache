package demo;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import kilim.Pausable;
import kilim.Task;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 *
* this example use google guava cache as the memory cache . the first request will invoke load() method in the CacheLoader
 * and after that the return value will be cached, the cached value will be refreshed every second. there's a inner
 * Scheduler will do this job .
 *
 * Created by adamshuang on 2018/8/9.
 */


public class GuavaCacheTest {
    Random random = new Random();
    public static final Object dummy = new Object();

    public final LoadingCache<String,Integer> loadingCache = CacheBuilder.newBuilder()
            .refreshAfterWrite(1,TimeUnit.SECONDS)
            .build(new KilimCacheLoader(
                    future -> {
                        Task.sleep(10);
                        future.set(random.nextInt(1000));
                    }
            ));



    public static class KilimCacheLoader<KK,VV> extends CacheLoader<KK,VV> {
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
    }

    public interface PausableFuture {
        void body(SettableFuture future) throws Pausable;
    }
    

    public static <KK,VV> VV get(LoadingCache<KK,VV> cache,KK key) throws Pausable {
        VV result = null;
        while (true) {
            try {
                result = cache.get(key);
                if (result==dummy)
                    cache.refresh(key);
                else
                    return result;
            }
            catch (ExecutionException ex) {}
            Task.sleep(100);
        }
    }
    
    
    public static void main(String[] args) throws Exception {
        if (kilim.tools.Kilim.trampoline(false,args))
            return;
        GuavaCacheTest cache = new GuavaCacheTest();

        Task.fork(() -> {
            while (true) {
                System.out.println(cache.get(cache.loadingCache,"any_key"));
                Task.sleep(100);
            }

        });

    }

}
