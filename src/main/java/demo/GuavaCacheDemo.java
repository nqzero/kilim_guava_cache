package demo;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import kilim.Task;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import static demo.KilimCacheLoader.getCache;

/**
 * stress test of the kilim-guava-cache-integration.
 * the first load() returns a dummy value which triggers an asynchronous reload.
 * all access is performed using the pausable getCache
 */
public class GuavaCacheDemo {
    
    public static void main(String[] args) throws Exception {
        if (kilim.tools.Kilim.trampoline(false,args))
            return;

        Random random = new Random();
        int numTasks = 1000;
        int maxIters = 100;
        int maxKey = 1100;
        int maxVal = 1000;
        int maxDelay = 1000;
        int maxSize = 1000;
        int refresh = 10;
        int maxWait = 1000;
        int retry = 200;

        LoadingCache<Integer,Integer> cache = CacheBuilder.newBuilder()
                .refreshAfterWrite(refresh,TimeUnit.SECONDS)
                .maximumSize(maxSize)
                .build(new KilimCacheLoader(
                        future -> {
                            Task.sleep(random.nextInt(maxDelay));
                            future.set(random.nextInt(maxVal));
                        }
                ));
        
        


        for (int jj=0; jj < numTasks; jj++) {
            int ktask = jj;
            Task.fork(() -> {
                while (true) {
                    int numIters = random.nextInt(maxIters);
                    for (int ii=0,sum=0; ii <= numIters; ii++) {
                        Task.sleep(random.nextInt(maxWait));
                        int key = random.nextInt(maxKey);
                        sum += getCache(cache,key,retry);
                        if (ii==numIters)
                            System.out.format("cache: %4d -> %8d\n",ktask,sum);
                    }
                }
            });
        }

    }

}
