---
layout: post
title: sbt and useCoursier - beware of slow startup and intellij import times
date: 2025-05-12
permalink: 2025/5/sbt-coursier-disabled-intellij-slow
tags: [scala, sbt]
---

tl;dr; you should really remove any remaining `useCoursier := false` from your `build.sbt`'s

sbt users beware: when sbt changed it's default dependency manager from ivy to coursier, it had a few bugs, causing some users (like us) to switch back to ivy. 

After a long bisecting session in a rather large build I noticed that this was the root issue to our very slow sbt startup and 'import into Intellij' times. Simply removing `useCoursier := false` from the `build.sbt` reduced the time to import the project into Intellij from 2:45mins down to 35s. That was somewhat surprising because I timed the bare import, without any fetching of dependencies...

These days the regular sbt / coursier combo is very mature, coursier is much better than ivy, so you really should not use ivy any longer. 
