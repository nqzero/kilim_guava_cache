package demo;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import kilim.Task;

import java.util.Random;
import static demo.KilimCacheLoader.getCache;
import demo.KilimCacheLoader.Body;

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
        int numTasks = 200;
        int maxIters = 1000;
        int maxKey = 1100;
        int maxDelay = 100;
        int maxSize = 1000;
        int maxWait = 100;
        int retry = 50;

        Cache<Integer,Double> cache = CacheBuilder.newBuilder()
                .maximumSize(maxSize)
                .build();
        
        

        Body<Integer,Double> getter = key -> {
            Task.sleep(random.nextInt(maxDelay));
            return key + random.nextDouble();
        };
        

        for (int jj=0; jj < numTasks; jj++) {
            int ktask = jj;
            Task.fork(() -> {
                while (true) {
                    int numIters = random.nextInt(maxIters);
                    double sum = 0;
                    for (int ii=1; ii <= numIters; ii++) {
                        Task.sleep(random.nextInt(maxWait));
                        int key = random.nextInt(maxKey);
                        sum += getCache(cache,getter,key,retry);
                        if (ii==numIters)
                            System.out.format("cache: %4d -> %8.3f\n",ktask,sum/numIters);
                    }
                }
            });
        }

    }

}
