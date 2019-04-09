---
layout: post
title: Understanding JVM soft references for great good (and building a cache)
date: 2019-04-09
permalink: 2019/04/09/understanding-jvm-soft-references-for-great-good-and-building-a-cache
tags: [jvm, java]
---

There are plenty of good and popular caching libraries on the JVM, including ehcache, guava and many others. However in some situations it's worth exploring other options. Maybe you need better performance. Or you want to allow the cache to grow and fill up the entire heap, yet shrink automatically when your application needs more space elsewhere. Typical caching libraries require you to define a static upper bound, which means you'll need to be very conservative with sizing your cache and still risk running into an `OutOfMemoryError`. They also need to keep track of the size of their elements themselves, which is potentially expensive.

One alternative are `soft references`. There are many [great articles](https://community.oracle.com/blogs/enicholas/2006/05/04/understanding-weak-references) about the different reference types: strong, soft, weak and phantom. In a nutshell, `strongly` referenced objects (the common case, e.g. `String s = "abc"`) are never collected by the garbage collector, and can therefor lead to an `OutOfMemoryError` if you allocate more than fit onto your heap. In contrast, `softly` referenced objects are collected as a last resort before an `OutOfMemoryError` is thrown.
You can create a soft reference using `SoftReference<String> softRef = new SoftReference<>("abc")`. To access the underlying object just call `softRef.get()`, which may return `null`. Note that if you (additionally) hold a `strong` reference to the same underlying object, it's not (only) `softly` referenced any more and can't be automatically freed.

'The internet' often discourages the use of soft references, typically without giving a good explanation, so I gave them a try and actually found them to be a good tool to have at my disposal. If you understand how soft references work, you can quite easily build a very simple and efficient cache, which has excellent performance and uses and frees up memory as required in other parts of your application. Before we look at that, let's discuss some common pitfalls with soft references.

## 1) Don't trust the [javadoc](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/ref/SoftReference.html).
Quote: >>> _All soft references to softly-reachable objects are guaranteed to have been freed before the virtual machine throws an OutOfMemoryError_ <<<
That's a lie. It was true when soft references were first introduced in java 1.2, but from java 1.3.1 the jvm property [`-XX:SoftRefLRUPolicyMSPerMB`](https://www.oracle.com/technetwork/java/hotspotfaq-138619.html#gc_softrefs) was introduced. It defaults to 1000 (milliseconds), meaning that if there's only 10MB available heap, the garbage collector will free references that have been used more than 10s ago. I.e. everything else will _not_ be freed, leading to an `OutOfMemoryError`, breaking the guarantee from the javadoc (I'll try to get that changed). 
No problem, let's just set it to `-XX:SoftRefLRUPolicyMSPerMB=0` and the javadoc is suddenly true again.

## 2) ... it's all or nothing
When the GC figures that memory is running low and it better frees some softly referenced objects, it will free _all_ of them. This will make our cache very inefficient, because it's expensive to recreate those objects. It would be better if the GC would only free a small portion of the available soft references. 

## Working around those issues to build a simple yet efficient cache
Since 1) is easily fixed, how do we fix the issue that the GC frees _all_ soft references? A very straightforward (if not the most efficient) approach is to simply hold *additional* strong references to the objects you *don't* want to get freed.
Obviously, we need to ensure that we drop these strong references if more memory is needed, e.g. when other softly referenced objects have been freed. That's why we override the `finalized` method, which get's invoked when the GC frees an object. As long as we always have some softly referenced objects, we'll not run into an `OutOfMemoryError`. 

Note that we could also perform other actions in `finalized`, like serializing the object somewhere. If you do, keep in mind that it must be a fast operation that doesn't require allocating a lot of additional memory, otherwise we're risking to run out of memory again. 

## Summary
Soft references are a simple and powerful concept on the JVM, and it's very useful to understand how they work. That's true not only if you want to build your own "poor man's cache", but also if you use "proper" caching libraries. Some even have the [option to use soft references](https://google.github.io/guava/releases/snapshot/api/docs/com/google/common/cache/CacheBuilder.html#softValues--) internally, in which case it's _essential_ to understand the caveats detailled above. 

Your default choice should still be a caching library, but if you need better performance, or want the cache to grow and fill up the entire heap, yet shrink automatically when your application needs more space elsewhere, soft references may be for you. Beware that they are rather low level, and come with their own set of tradeoffs. As always: choose the best tool for the job, and keep soft references in the back of your head (literally). 

Here's a simple example for such a cache, if you want to try this yourself:

```java
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;

public class FinalizedTest {
  static final int count = 5000000;

  /**
   * to get usable output, run with e.g. `-Xms1g -Xmx1g -XX:SoftRefLRUPolicyMSPerMB=0`
   */
  public static void main(String[] args) throws Exception {
    ArrayList<SoftReference<Instance>> instances = new ArrayList<>();
    long start = System.currentTimeMillis();

    for (int i = 0; i<count; i++) {
      instances.add(new SoftReference<>(new Instance()));

      if (i % 100000 == 0) {
        Thread.sleep(100); // in lieu of other application usage
        System.out.println(i + " instances created (in total); free=" + Runtime.getRuntime().freeMemory() / 1024 / 1024 + "M");
      }
    }

    System.out.println("time taken: " + (System.currentTimeMillis() - start));
  }

}

class Instance {
  static int finalizedCount = 0;
  String[] occupySomeHeap = new String[50];

  public Instance() {
    InstanceReferenceManager.register(this);
  }

  @Override
  protected void finalize() throws Throwable {
    super.finalize();
    InstanceReferenceManager.notifyObjectFinalized();
    finalizedCount++;
    if (finalizedCount % 100000 == 0) {
      System.out.println(finalizedCount + " instances finalized (in total)");
    }
  }
}

/**
 * By default, the JVM will free *all* soft references when it runs low on memory.
 * That's a waste of resources, because they'll need to be deserialized back from disk, which is expensive.
 * Instead, we want the GC to only free a small percentage, and therefor we hold onto the rest via strong references.
 */
class InstanceReferenceManager {
  /** maximum number of instances that can remain `soft` in the wild, i.e. we won't hold a strong reference to them */
  public static final int MAX_WILD_SOFT_REFERENCES = 500000;

  private static int observedInstanceCount = 0;
  private static final ConcurrentLinkedDeque<Instance> strongRefs = new ConcurrentLinkedDeque<>(); //using LinkedList because `Iterator.remove(Object)` is O(1)

  /** called from `Instance` constructor */
  public static void register(Instance instance) {
    observedInstanceCount++;
    if (observedInstanceCount > MAX_WILD_SOFT_REFERENCES) {
      // hold onto a strong reference to this instance, so that it doesn't get freed by the GC when we're low on memory
      strongRefs.add(instance);
    }
  }

  /**
   * Called from `Instance.finalize()`, i.e. either an instance got normally finalized or it got
   * freed by the GC because we're low on heap.
   * I.e. there are now potentially less 'only softly reachable instances' in the wild, so we should
   * free some strong references (if we have any).
   */
  public static void notifyObjectFinalized() {
    observedInstanceCount--;
    Iterator<Instance> iterator = strongRefs.iterator();
    if (iterator.hasNext()) {
      iterator.next();
      iterator.remove();
    }
  }
}

/*
truncated output for `-Xms1g -Xmx1g -XX:SoftRefLRUPolicyMSPerMB=0`
2300000 instances created (in total); free=532M
1500000 instances finalized (in total)
2900000 instances created (in total); free=327M
1600000 instances finalized (in total)
3000000 instances created (in total); free=457M
1800000 instances finalized (in total)
*/
```

This was first published on the [ShiftLeft blog](https://blog.shiftleft.io/understanding-jvm-soft-references-for-great-good-and-building-a-cache-244a4f7bb85d)
