package demo;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import kilim.Task;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import static demo.KilimCacheLoader.getCache;

/**
 *
* this example use google guava cache as the memory cache . the first request will invoke load() method in the CacheLoader
 * and after that the return value will be cached, the cached value will be refreshed every second. there's a inner
 * Scheduler will do this job .
 *
 * Created by adamshuang on 2018/8/9.
 */


public class GuavaCacheTest {
    
    public static void main(String[] args) throws Exception {
        if (kilim.tools.Kilim.trampoline(false,args))
            return;

        Random random = new Random();

        LoadingCache<String,Integer> cache = CacheBuilder.newBuilder()
                .refreshAfterWrite(1,TimeUnit.SECONDS)
                .build(new KilimCacheLoader(
                        future -> {
                            Task.sleep(10);
                            future.set(random.nextInt(1000));
                        }
                ));
        
        
        
        Task.fork(() -> {
            while (true) {
                int val = getCache(cache,"any_key",50);
                System.out.println(val);
                Task.sleep(100);
            }

        });

    }

}
