package demo;

import com.google.common.cache.CacheBuilder;
import kilim.Task;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * stress test of the kilim-guava-cache-integration.
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
        int refresh = 10000;
        
        KilimCacheLoader<Integer,Double> loader = new KilimCacheLoader(
                CacheBuilder.newBuilder()
                        .refreshAfterWrite(refresh,TimeUnit.MICROSECONDS)
                        .maximumSize(maxSize));

        loader.setReloader(key -> {
            Task.sleep(random.nextInt(maxDelay));
            return key+random.nextDouble();
        });

        for (int jj=0; jj < numTasks; jj++) {
            int ktask = jj;
            Task.fork(() -> {
                while (true) {
                    int numIters = random.nextInt(maxIters);
                    double sum = 0;
                    for (int ii=1; ii <= numIters; ii++) {
                        Task.sleep(random.nextInt(maxWait));
                        int key = random.nextInt(maxKey);
                        sum += loader.get(key);
                        if (ii==numIters)
                            System.out.format("cache: %4d -> %8.3f\n",ktask,sum/numIters);
                    }
                }
            });
        }

    }

}
