package demo;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import kilim.Mailbox;
import kilim.Pausable;
import kilim.Task;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 *
* this example use google guava cache as the memory cache . the first request will invoke load() method in the CacheLoader
 * and after that the return value will be cached , the cached value will be refreshed every 2 minits. there's a inner
 * Scheduler will do this job .
 *
 * Created by adamshuang on 2018/8/9.
 */


public class GuavaCacheTest {

    public final LoadingCache<String, Integer> loadingCache =
            CacheBuilder.newBuilder()
                    .refreshAfterWrite(2, TimeUnit.MINUTES)
                    .build(new KilimCacheLoader());



    class KilimCacheLoader extends CacheLoader<String, Integer> {


        /**
         * this method won't work because the meothod call this 'load()' method doesn't throw pausble itself ,
         * it tried to rewrite it , but it seems not feasible because there's tons of codes to change .
         *
         * of course i could use 'getb()' to avoid throw pausable , but it will block the thread. this will bring
         * lots of problem in a busy server . many task can't be executed .
         *
         * so is there any smart way to to this ?
         *
         * @param key
         * @return
         * @throws Exception
         * @throws Pausable
         */
        @Override
        public Integer load(String key) {
            // inner class method , need
            LoadTask loadTask = new LoadTask();
            loadTask.start();

            return loadTask.calcValue.getb();
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
            loadTask.calcValue.getb();

            SettableFuture settableFuture = SettableFuture.create();
            settableFuture.set(loadTask.calcValue.getb());

            return settableFuture;
        }

    }

    class LoadTask extends Task {

        Random random = new Random();
        Mailbox<Integer> calcValue = new Mailbox<>();

        @Override
        public void execute() throws Pausable {
            sleep(10);
            calcValue.put(random.nextInt(1000));
        }
    }

    public static void main(String[] args) throws ExecutionException {
        if (kilim.tools.Kilim.trampoline(false,args))
            return;
        GuavaCacheTest guavaCacheTest = new GuavaCacheTest();

        while (true) {
            System.out.println(guavaCacheTest.loadingCache.get("any_key"));
        }


    }

}
