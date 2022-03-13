# GC日志分析

## 不同GC、堆内存下GC情况

### Serial

|          | 512M              | 1G                | 4G               |
| -------- | ----------------- | ----------------- | ---------------- |
| Young GC | 20次，avg: 7.5ms  | 16次，avg: 21.2ms | 4次，avg: 82.5ms |
| Full GC  | 17次，avg: 28.2ms | 3次，avg: 40ms    | 0                |
| Total    | 630ms             | 460ms             | 330ms            |

### Parallel Scavenge + Parallel Old

|          | 512M              | 1G                | 4G               |
| -------- | ----------------- | ----------------- | ---------------- |
| Young GC | 23次，avg: 4.78ms | 31次，avg: 11.9ms | 6次，avg: 38.3ms |
| Full GC  | 16次，avg: 38.1ms | 2次，avg: 50ms    | 0                |
| Total    | 720ms             | 470ms             | 230ms            |

### CMS

|          | 512M              | 1G                | 4G               |
| -------- | ----------------- | ----------------- | ---------------- |
| Young GC | 36次，avg: 6.23ms | 21次，avg: 17.6ms | 未触发老年代回收 |
| Full GC  | 11次，avg: 35.2ms | 2次，avg: 36.3ms  | 未触发老年代回收 |
| Total    | 612ms             | 442ms             | 未触发老年代回收 |

### G1

|          | 512M              | 1G                | 4G              |
| -------- | ----------------- | ----------------- | --------------- |
| Young GC | 61次，avg: 3.32ms | 19次，avg: 6.57ms | 14次，avg: 19ms |
| Mixed GC | 15次，avg: 4.46ms | 21次，avg: 6.46ms | 0               |
| Full GC  | 4次，avg: 29.5ms  | 0                 | 0               |
| Total    | 517ms             | 300ms             | 266ms           |

## 堆内存与GC耗时

可以很明显看出，使用同一种垃圾收集器时，堆内存越大，GC次数越少（无论是Young GC还是Full GC）。但同时，由于堆内存的增加，导致触发GC的内存阈值也相应扩大，使得每次在GC时需要回收的内存也越多，因此导致单次GC的耗时增加。总体来看，堆内存的增加带来的GC次数减少的收益大于单次GC耗时增加的成本。

## 垃圾收集器比较

### Serial与Parallel

在同样堆内存的情况下，对比各个垃圾回收器的表现，很明显看到Java8默认的垃圾收集器Parallel Scavenge的表现并不是很理想。在堆内存较低的情况下，相比于Serial发生了较多次的Young GC，且GC的整体耗时也明显长于Serial。

这样的表现并不是很符合预期，按理来说Parallel Scavenge使用了多线程进行垃圾回收，没有理由比Serial还慢。但后来通过`-XX:PrintGCDetails`打印出详细的GC日志后，我从中发现了一些端倪：

```
// Serial 512M
def new generation   total 157248K, used 41668K [0x00000000e0000000, 0x00000000eaaa0000, 0x00000000eaaa0000)
  eden space 139776K,  29% used [0x00000000e0000000, 0x00000000e28b1308, 0x00000000e8880000)
  from space 17472K,   0% used [0x00000000e8880000, 0x00000000e8880000, 0x00000000e9990000)
  to   space 17472K,   0% used [0x00000000e9990000, 0x00000000e9990000, 0x00000000eaaa0000)
  
// Parallel 512M
PSYoungGen      total 116736K, used 2760K [0x00000000f5580000, 0x0000000100000000, 0x0000000100000000)
  eden space 58880K, 4% used [0x00000000f5580000,0x00000000f5832358,0x00000000f8f00000)
  from space 57856K, 0% used [0x00000000fc780000,0x00000000fc780000,0x0000000100000000)
  to   space 57856K, 0% used [0x00000000f8f00000,0x00000000f8f00000,0x00000000fc780000)
```

上面分别列出了使用Serial和PS的新生代内存使用情况，一般情况下，新生代的内存使用为全部堆内存大小的1/3(NewRatio=2)，并且Eden区和from、to区的比例应该为8:1:1(SurvivorRatio=8)，但是PS的内存使用情况却不是这样的：新生代的内存只有116M左右，不到堆内存的1/3，并且eden区和from/to区的比例接近1:1，而不是之前认为的8:1。事实上，这和默认的JVM选项有关，在没有显式指定垃圾收集器，使用默认的Parallel Scavenge+Parallel Old收集器时，JVM会打开UseAdaptiveSizePolicy开关，这就导致新生代、Eden区的大小会在运行中动态调整。正是由于这种调整减小了新生代和Eden区的内存大小，从而导致Parallel Scavenge触发了多次Young GC，并最终导致GC总体耗时的增加。

UseAdaptiveSizePolicy的资料可参考：https://www.jianshu.com/p/7414fd6862c5

由于AdaptiveSizePolicy的存在，导致了在堆内存相对较低时，Parallel的表现反而不如Serial，但当堆内存较大，Paralle并行处理的优势就能充分体现出来，平均的Young GC时长为38.3ms，而Serial的Young GC平均时长则为82.5ms。

### Parallel和CMS

在老年代的收集上，就实验结果来看，CMS相比Parallel Old要优秀一些， Full GC的平均时间较短，即使是在关闭了AdaptiveSizePolicy之后也是如此。

### G1与其他垃圾收集器

可以看出，相比其他垃圾收集器，在总体的GC耗时上，G1都有显著优势，但是GC的次数相比于其他垃圾收集器都要多，这可能是由于G1中Eden、Survivor乃至老年代的空间大小都是不固定的，由于空间的调整导致Young GC/Mixed GC发生的次数较多。