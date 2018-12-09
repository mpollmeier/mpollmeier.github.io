---
layout: post
title: Calculating the checksum (hash/md5/sha1) of directories in Scala
date: 2018-12-10
permalink: 2018/12/10/checksum-files-scala
tags: [scala, files]
---
I had the need to generate the checksum of multiple directories, recursively. This should really be in the (java) standard library, but I had to collect crumbs from multiple locations and port it to Scala. To spare others from having to do the same, here's a copy-pastable snippet:

```scala
import java.io.File
import java.nio.file.Files
import java.security.{DigestInputStream, MessageDigest}

def md5(roots: File*): String = {
  val md = MessageDigest.getInstance("MD5")
  roots.foreach { root =>
    Files.walk(root.toPath).filter(!_.toFile.isDirectory).forEach { path =>
      val dis = new DigestInputStream(Files.newInputStream(path), md)
      // fully consume the inputstream
      while (dis.available > 0) {
        dis.read
      }
      dis.close
    }
  }
  md.digest.map(b => String.format("%02x", Byte.box(b))).mkString
}

// usage:
md5(new File("/path/to/dir1"))
```
