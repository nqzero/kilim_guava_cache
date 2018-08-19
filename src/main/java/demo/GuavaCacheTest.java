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

    class KilimCacheLoader extends CacheLoader<String, Integer> {


        /**
         * guava doesn't support an async load, so just return a dummy value
         *
         * @param key
         * @return
         * @throws Exception
         * @throws Pausable
         */
        @Override
        public Integer load(String key) {
            return dummy;
        }

        /**
         * this method will reload the cache asynchronously , it should not block the main stream either .
         * @param key
         * @param oldValue
         * @return
         * @throws Exception
         * @throws Pausable
         */
        @Override
        public ListenableFuture reload(String key, Integer oldValue) {
            LoadTask loadTask = new LoadTask();
            loadTask.start();
            return loadTask.future;
        }

    }

    class LoadTask extends Task {
        SettableFuture future = SettableFuture.create();
        Random random = new Random();

        @Override
        public void execute() throws Pausable {
            sleep(10);
            future.set(random.nextInt(1000));
        }
    }

    /**
     * get a value from the cache, retrying until it loads
     * @param key
     * @return the value associated with the key
     * @throws Pausable 
     */
    public Integer get(String key) throws Pausable {
        Integer result = null;
        while (true) {
            try {
                result = loadingCache.get(key);
            }
            catch (ExecutionException ex) {}
            if (result==dummy)
                loadingCache.refresh(key);
            else if (result==null)
                System.out.println("retry");
            else
                return result;
            Task.sleep(100);
        }
    }
    
    class QuizTask extends Task {
        public void execute() throws Pausable {
            while (true) {
                Integer val = get("any_key");
                System.out.println(val);
                Task.sleep(100);
            }
        }
    }
    
    
    public static void main(String[] args) throws Exception {
        if (kilim.tools.Kilim.trampoline(false,args))
            return;
        GuavaCacheTest guavaCacheTest = new GuavaCacheTest();

        guavaCacheTest.new QuizTask().start();

    }

}
