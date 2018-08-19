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

    public final LoadingCache<String, Integer> loadingCache =
            CacheBuilder.newBuilder()
                    .refreshAfterWrite(1, TimeUnit.SECONDS)
                    .build(new KilimCacheLoader());


    public static final Integer dummy = new Integer(-42883);
    Random random = new Random();

    class KilimCacheLoader extends CacheLoader<String, Integer> {
        public Integer load(String key) {
            return dummy;
        }

        public ListenableFuture reload(String key, Integer oldValue) {
            SettableFuture future = SettableFuture.create();
            Task.fork(() -> {
                Task.sleep(10);
                future.set(random.nextInt(1000));
            });
            return future;
        }

    }

    public Integer get(String key) throws Pausable {
        Integer result = null;
        while (true) {
            try {
                result = loadingCache.get(key);
                if (result==dummy)
                    loadingCache.refresh(key);
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
                System.out.println(cache.get("any_key"));
                Task.sleep(100);
            }

        });

    }

}
