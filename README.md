### Electrons使用指南

#### 前言<br/>
Electrons是一个异步高性能事件总线，结合了生产者消费者模型和SUB/PUB模式。传统的SUB/PUB模型，在publish事件的时候是直接同步执行所有的监听器，是**同步阻塞**的。现在结合RINGBUFFER的无锁的急速体验，设计了Electrons，意味电路，事件就相当于跑在电路中的电子，电子在电路中的传递速度非常之快，这也是这个插件的寓意。publish的事件会放到容器中等待消费者消费，消费者就是SUB方的listener，这里有个设计上的小缺陷，由于种种原因，listener的注册是通过注解 + 接口的方式实现，后面会提到。下面进入主题<br/>

#### 使用<br/>
基本使用：<br/>

```
EleCircuit eleCircuit = new EleCircuit();
eleCircuit.start();
boolean ok1 = eleCircuit.publish("tag1", new LongEvent(1));
boolean ok2 = eleCircuit.publish("tag1", new LongEvent(2));
System.out.println(ok1);
System.out.println(ok2);
eleCircuit.stop();
```

具体的限流熔断以及电路相关配置放在`Config`类中，创建实例的时候也可以使用：<br/>

```
Config config = new Config();
config.set...
config.set...
/*省略配置信息*/

EleCircuit eleCircuit = new EleCircuit(config);
```
**注意**：这里现在已经全部使用TypeSafe在.conf文件中配置了！

Event需要继承`Electron`抽象类，举个例子：<br/>

```
public class LongEvent extends Electron {

    @Getter
    private String name;

    /**
     * Constructs a prototypical Event.
     *
     * @param source The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public LongEvent(Object source) {
        super(source);
        this.name = "LongEvent" + source;
    }
}
```

其他例如事件的权重，可以在event中设置，方法是：<br/>

```
    setWeight(int weight) {
        this.weight = weight;
    }
```

这个属性仅仅在限流开启的时候有效，否则设置了也不生效，可以根据事件的大小来划定权重。<br/>

再看看监听器，这个比较重要，先举个例子：<br/>

```
Listener(subscribe = "tag1")
public class LongEventListener implements ElectronsListener<LongEvent> {

    @Override
    public void onEvent(LongEvent electrons) throws Exception {
        System.out.println("ele name : " + electrons.getName() + "Source : " + electrons.getSource());
    }
}
```
`@Listener`注解用来标注监听器，类需要实现ElectronsListener接口，并且**切记加上泛型的限制**，否则会出问题！在OnEvent方法中实现处理事件的方法即可。<br/>

再看一下`@Listener`注解，这个注解主要限定了一些Listener的属性值，先看一下`@Listener`：<br/>

```
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Listener {

    /**
     * 标识一个唯一的listener
     *
     * @return 这个listener的id
     */
    String id() default "";

    /**
     * after标识这个listener在某个listener之后执行，返回一个string，是id的集合，用【,】分割
     *
     * @return 要在该listener之前执行的listener的id的集合
     */
    String after() default "";

    /**
     * 优先级
     *
     * @return listener的优先级
     */
    int priority() default Byte.MIN_VALUE;

    /**
     * 订阅的类型
     *
     * @return 订阅的事件类型
     */
    String subscribe() default "";
}
```

1. 先看一下`subscribe`属性，这个属性规定了订阅的事件的`tag`，**最好不好为默认值**。<br/>
2. 再看一下`priority`属性，这个属性规定了监听器的**优先级**，**优先级高的监听器优先执行，但不保证优先完成**！而且在`after`属性存在的情况下，priority可能会失效！<br/>
3. 下面一起来说`id`属性和`after`属性，这两个属性大部分情况下要配合使用。我们来先看一种场景：事件A需要被监听器L1和L2订阅，但是*L2的执行依赖L1的结果或者L2需要L1执行之后再执行*。这是就需要使用我们的这两个属性类配合使用，我们先把L1的`id`设置为**L1**，再在L2的`after`属性中，写入`L1`，即规定L2监听器需要在id = “L1”的执行器执行之后执行。<br/>

**注意：如果L2为尾节点，则L2不能存在`id`属性！**<br/>

这里举一个复杂的例子：公有监听器A、B、C、D、E：<br/>
* B、C必须在A之后执行<br/>
* D必须在B之后执行<br/>
* E必须在D、C之后执行<br/>
* （**执行之后的意思就是执行完了之后**）<br/>

这种情况下的`id`和`after`属性：<br/>
* A的`id` -> 1，**没有`after`属性**<br/>
* B的`id` -> 2，`after`属性为 -> "1"<br/>
* C的`id` -> 3，`after`属性为 -> "1"<br/>
* D的`id` -> 4，`after`属性为 -> "2"<br/>
* **E的`id` 不能存在**！`after`属性为 -> "3,4"（如果在多个监听器执行之后执行，`after`中的id用 **[ , ]** 隔开）<br/>

模式图应该为下面这种情况：<br/>
![Alt text](./监听器模型图.png)
<br/>

#### 其他使用注意事项<br/>
1. 代理类的使用：提供了两种代理发布器，生产者消费者模型也存在容器满了的情况，这种情况如果直接使用`publish`方法需要用户自己处理异常，比较麻烦，现在提供了：**重发代理类**和**丢弃代理类**。作用：<br/>
* 重发：先发布一次事件，如果容器满了，休息500ms再发一次，如果继续满，使用同步发送。<br/>
* 丢弃：只发布一次事件，如果容器满了，直接记录日志并返回，事件丢弃（不推荐，只在小部分场景使用）。<br/>
2. 同步发布事件的说明：提供了同步发送的传统方式，这种方式会循环所有的监听器，*同步阻塞，直到所有的监听器执行完成*。由于上述那种A监听器执行完再执行监听器B的逻辑依赖disruptor的实现，所以**同步发布不支持存在after逻辑的监听器！**<br/>

#### 架构设计<br/>
![Alt text](./electrons架构图.png)
<br/>

##### 关于限流<br/>
令牌桶算法的实现，针对**全部通道**，每个通道都有一个限流器（**包括NormalChannel**）。在Config中配置之后即可开启，现在没有针对某一个通道的单独配置，后期考虑加入。<br/>

##### 关于熔断<br/>
上文提到过，只有特殊通道支持熔断，这里阐述一下设计理念：<br/>

**为什么对普通通道不设计熔断**：普通通道存放的事件的监听器是没有关联的，即只要监听器链中存在`after`逻辑的监听器，就不会放到普通通道中。如果因为某个监听器持续出错，把整个通道熔断，其实是非常不合理的，因为其他监听器跟持续出错的监听器之间没有任何联系，不应该一起被熔断，其他监听器应该允许继续执行。<br/>

**为什么对特殊通道设计熔断**：原理类似，由于特殊通道是存在after逻辑的通道，即监听器之间存在关联，如果中间或前置监听器出问题，很可能导致后继监听器处理事件出错，或者无法处理事件，这时候熔断整个通道，提醒用户去解决问题。<br/>

#### 关于<br/>
[源码地址](https://github.com/carryxyh/Electrons)<br/>
相关问题联系我的邮箱：*carryxyh@gmail.com*<br/>
