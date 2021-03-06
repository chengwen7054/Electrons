package com.ziyuan.base;

import com.ziyuan.Config;
import com.ziyuan.EleCircuit;
import com.ziyuan.after.IntEvent;
import com.ziyuan.breaker.DoubleEvent;
import com.ziyuan.ex.EleEvent;
import com.ziyuan.priority.LongEvent2;
import com.ziyuan.rateLimit.StringEvent;
import junit.framework.TestCase;

import java.util.concurrent.TimeUnit;

/**
 * BaseTest
 *
 * @author ziyuan
 * @since 2017-03-20
 */
public class BaseTest extends TestCase {

    public void testBase() throws Exception {
//        EleCircuit eleCircuit = new EleCircuit();
//        eleCircuit.start();

        EleCircuit eleCircuit = EleCircuit.ready();
        boolean ok1 = eleCircuit.publish("tag1", new LongEvent(1));
        boolean ok2 = eleCircuit.publish("tag1", new LongEvent(2));
        System.out.println(ok1);
        System.out.println(ok2);
        //输入任意字符停止
        System.in.read();
        eleCircuit.stop();
    }

    public void testPriority() throws Exception {
//        EleCircuit eleCircuit = new EleCircuit();
//        eleCircuit.start();

        EleCircuit eleCircuit = EleCircuit.ready();
        boolean ok1 = eleCircuit.publish("tag2", new LongEvent2(1));
        boolean ok2 = eleCircuit.publish("tag2", new LongEvent2(2));
        System.out.println(ok1);
        System.out.println(ok2);
        eleCircuit.stop();
    }

    public void testAfter() throws Exception {
//        EleCircuit eleCircuit = new EleCircuit();
//        eleCircuit.start();

        EleCircuit eleCircuit = EleCircuit.ready();
//        boolean ok1 = eleCircuit.publish("after", new IntEvent(123));
        boolean ok2 = eleCircuit.publish("after", new IntEvent(456));
//        boolean ok2 = eleCircuit.publish("after", new IntEvent(456));
//        System.out.println(ok1);
        System.out.println(ok2);
        //输入任意字符停止
        System.in.read();
        eleCircuit.stop();
    }

    public void testRateLimit() throws Exception {
        Config config = new Config();
        config.setLimitRate(true);
        config.setPermitsPerSecond(2);
        config.setWarmup(true);

//        EleCircuit eleCircuit = new EleCircuit(config);
//        eleCircuit.start();

        EleCircuit eleCircuit = EleCircuit.ready();
        StringEvent e1 = new StringEvent("weight 10");
        e1.setWeight(10);
        StringEvent e2 = new StringEvent("weight 20");
        e2.setWeight(20);
        boolean ok1 = eleCircuit.publish("rateLimit", e1);
        boolean ok2 = eleCircuit.publish("rateLimit", e2);
        System.out.println(ok1);
        System.out.println(ok2);
        eleCircuit.stop();
    }

    public void testBreaker() throws Exception {
        Config config = new Config();
        config.setBreaker(true);

        config.setErrorNum(2);
        config.setPerUnit(1);
        config.setUnit(TimeUnit.MINUTES);

        config.setCloseThreshold(2);

        config.setRest(10);
        config.setRestUnit(TimeUnit.SECONDS);

//        EleCircuit eleCircuit = new EleCircuit(config);
//        eleCircuit.start();

        EleCircuit eleCircuit = EleCircuit.ready();
        eleCircuit.publish("breaker", new DoubleEvent(1.0));
        eleCircuit.publish("breaker", new DoubleEvent(2.0));
        eleCircuit.publish("breaker", new DoubleEvent(3.0));

        //4以后的会熔断

        eleCircuit.publish("breaker", new DoubleEvent(4.0));
        eleCircuit.publish("breaker", new DoubleEvent(5.0));
        eleCircuit.publish("breaker", new DoubleEvent(6.0));
        eleCircuit.publish("breaker", new DoubleEvent(7.0));
        eleCircuit.publish("breaker", new DoubleEvent(8.0));

        System.out.println("\n");
        Thread.sleep(12000);
        System.out.println(" after sleeps \n");

        eleCircuit.publish("breaker", new DoubleEvent(99.0));
        eleCircuit.publish("breaker", new DoubleEvent(98.0));
        eleCircuit.publish("breaker", new DoubleEvent(97.0));

        eleCircuit.publish("breaker", new DoubleEvent(96.0));
        eleCircuit.publish("breaker", new DoubleEvent(95.0));
        eleCircuit.publish("breaker", new DoubleEvent(94.0));
        eleCircuit.publish("breaker", new DoubleEvent(93.0));

        eleCircuit.stop();
    }

    /**
     * 如果我们在handleEventException中抛出一个运行时异常，load会飙升到100%，我们jstack这个java线程，会发现只有一个是runnable状态，而其他线程都在wait。
     * 我们可以从jstack中看到，是在liteWaitStrategy中的56行park住了，查看代码我们可以看到，这一行执行了：
     * processorNotifyCondition.await();
     * <p>
     * 而runnable的部分，则一直在执行：
     * while ((availableSequence = dependentSequence.get()) < sequence)
     * {
     * barrier.checkAlert();
     * }
     * <p>
     * 这段代码在执行的就是，等待前一个消费者执行完成，由于抛异常，导致上一个消费者break掉了，所以上一个消费者用于不会返回
     *
     * @throws Exception
     */
    public void testEx() throws Exception {
        EleCircuit eleCircuit = EleCircuit.ready();
        for (; ; ) {
            Thread.sleep(100);
            eleCircuit.publish("ex", new EleEvent(1));
        }
    }
}
