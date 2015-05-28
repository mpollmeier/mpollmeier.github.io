---
layout: post
title: Selecting top k items from a list efficiently in Java / Groovy
permalink: selecting-top-k-items-from-a-list-efficiently-in-java-groovy
date: 2011-12-23 02:28:31.000000000 +01:00
tags: [java, groovy, algorithm]
---
I recently ran into an interesting problem to solve for my side project: how can I efficiently select the top k elements from a very large list in Java / Groovy?
There are many recommendations about how to do that, but I didn't find a comparison of the suggested implementations. This is a crucial problem for my application so I tried out a number of them and here are the results. I chose Groovy for it's syntactical sugar with collections and closures, but you can do the same in plain Java. The <a href="https://github.com/mpollmeier/Selection-Algorithms">code is on github</a>, you can run it with a simple <em>mvn compile groovy:execute</em> if you want to play around yourself. 


<h3>Task: select the top 5 elements from a list of 10 million</h3>

<h3>Plain sort: 6,500ms</h3>
Sorting the whole list and picking the top elements is okay for small lists, but it doesn't scale for larger lists as the time for sorting a list grows with O(nlogn). For a list with 10 million numbers, it's completely out of the competition. 

``` groovy
    topElements = unorderedList.sort()[numberCount-1..numberCount-k]
```

<h3>(QuickSelect: 2,500ms)</h3>
Also called Hoares Selection Algorithm, was mentioned in a few articles, so wanted to give it a try. However I only found implementations for educational purposes like <a href="http://www.brilliantsheep.com/java-implementation-of-hoares-selection-algorithm-quickselect/">this one</a>. It's probably not fair to compare this demo implementation with the highly optimized other ones that follow, so I put the result in brackets as I don't want to discredit the algorithm. 
[source too long to quote here, see <a href="http://www.brilliantsheep.com/java-implementation-of-hoares-selection-algorithm-quickselect/">article on brilliantsheep.com</a>]

<h3>(Heap Select: 2,200ms)</h3>
Again, this is just a demonstration on how to implement a heap select based on <a href="http://stevehanov.ca/blog/index.php?id=122">Steve Hanov's great article</a>. I've ported his Python implementation to Groovy, which was easy enough. I'm sure there is a more efficient way to do a Heap Select - the old rule holds true: don't try and implement it yourself if someone else brighter has done it already for you. Anyway, this was my naive attempt:

``` groovy
    def heapSelect(List list, k) {
        def heap = new PriorityQueue(k)
        list.each{ item ->
            if (heap.size() < k || item > heap.peek()) {
                if (heap.size() == k)
                    heap.remove(heap.peek())
                heap.offer(item)
            }
        }
        return heap as List
    }
```

<h3>PriorityQueue: 300ms</h3>
<a href="http://docs.oracle.com/javase/7/docs/api/java/util/PriorityQueue.html">Ships</a> with the JRE, so there's no need for external libraries. It let's you add a list of elements to a priority heap and then poll the top element from the heap one by one. Simple and fast!

``` groovy
    heap = new PriorityQueue(unorderedList.size())
    heap.addAll(unorderedList)
    topElements = (1..k).collect{heap.poll()}
```

<h3>Guava Ordering: 170ms</h3>
Google Guava (formerly Google Collections) comes with an <a href="http://guava-libraries.googlecode.com/svn/trunk/javadoc/com/google/common/collect/Ordering.html">Ordering</a> class that works even faster than the PriorityQueue for our task. Under the hood it seems to use QuickSort to sort only parts of a collection, however I haven't dug too deep in their implementation. 
An obstacle on using this could be that your data has to be in a structure that implements <a href="http://docs.oracle.com/javase/7/docs/api/java/lang/Iterable.html">Iterable</a>, e.g. an ArrayList. If you have a plain int[] and need to convert it first, you might be better off to go for a PriorityQueue in the first place. On the other hand, maybe an <a href="http://commons.apache.org/collections/apidocs/org/apache/commons/collections/iterators/ArrayIterator.html">ArrayIterator</a> can do this very efficiently.. haven't tried it out though.

``` groovy
com.google.common.collect.Ordering.natural()
    .greatestOf(arrayList, k)
```


<h3>By the way...</h3>
I often saw the suggestion to add the elements into a TreeMap which orders them for you. While this works fine it will never be the most efficient solution because it sorts <em>all</em> data in the map, whereas we're only interested in the highest k items. 

Please bear in mind that I put all this together at home on my own, so if you find any mistakes please leave a comment or drop me a mail and I'm happy to update this entry - and give you all the credit, of course ;) 
Again: the <a href="https://github.com/mpollmeier/Selection-Algorithms">code is on github</a> - all it takes is <em>mvn compile groovy:execute</em> to run it on your machine. 

<h3>Comment from Sebastiano Vigna on 23/05/2015</h3>
Just as a reference, on my hardware extracting 5 top elements out of 10M distinct integer objects...

* Takes 586ms using a Java PriorityQueue and addAll.
* Takes 400ms using a Java PriorityQueue, but creating it using the constructor based on a collection. Making a vector into a heap is O(n), whereas adding n elements is O(n log n), so by using addAll() you're wasting a log n factor. The constructor based on collections is there for that purpose. The time is then O(n + k log n), similarly to Guavas's solution.
* Takes 360ms using fastutil's ObjectHeapPriorityQueue, which is a better implementation of a heap than java.util's (using the constructor based on collections).
* Takes 200ms using Guava.
* Takes 71ms using fastutil's IntHeapPriorityQueue, which doesn't use objects.

In general, I think that primitive collections are unbeatable. But in any case you can gain a lot by using the O(n) heap construction. I guess that Guava's strategy implemented with primitive types would be faster than anything else. I hope this is useful information!

Ciao, seba

PS: If instead of 10M distinct integers I use random integers from a smaller range times decrease considerably, which I think is the reason for the difference in timings with your results.
