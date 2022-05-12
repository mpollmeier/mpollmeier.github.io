---
layout: post
title: Calculating the checksum (hash/md5/sha) of directories in Scala
date: 2018-12-10
permalink: 2018/12/10/checksum-files-scala
tags: [scala, files]
---
Updated on 12 May 2022 with a far more efficient version, and using sha256:

I had the need to generate the checksum of multiple directories, recursively. This should really be in the (java) standard library, but I had to collect crumbs from multiple locations and port it to Scala. To spare others from having to do the same, here's a copy-pastable snippet:

```scala
import java.nio.file.{Files, Path}
import java.security.{DigestInputStream, MessageDigest}
import scala.util.Using

def sha256(path: String): String =
  sha256(Path.of(path))

def sha256(path: Path): String =
  sha256(Seq(path): _*)

def sha256(roots: Path*): String = {
  val md = MessageDigest.getInstance("SHA-256")
  val buffer = new Array[Byte](4096)
  roots.foreach { root =>
    Files.walk(root).filter(!_.toFile.isDirectory).forEach { path =>
      Using.resource(new DigestInputStream(Files.newInputStream(path), md)) { dis =>
        // fully consume the inputstream
        while (dis.available > 0) {
          dis.read(buffer)
        }
      }
    }
  }
  md.digest.map(b => String.format("%02x", Byte.box(b))).mkString
}

// usage:
sha256("/path/to/dir1")
```
