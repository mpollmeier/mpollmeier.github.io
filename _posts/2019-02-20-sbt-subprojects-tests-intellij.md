---
layout: post
title: Working dir for tests between sbt and intellij for subprojects
date: 2019-02-20
permalink: 2019/02/20/sbt-subprojects-tests-intellij
tags: [scala, intellij, sbt]
---
When running tests from sbt it's typically a good idea to fork the jvm, e.g. to make sure there's no nasty global shared state or other leftovers hanging around from the last test. To do so, many `build.sbt` contain the setting `Test/fork := true`. 

That however also means that the baseDirectory (where the test is executed from) changes to the subproject directory (see https://github.com/sbt/sbt/issues/3892). Which would be totally fine, if only Intellij Idea would follow the same logic. This is not to blame one tool or another, it's merely an inconvenience.

The easiest fix I could find was to instruct sbt to use the project root, (rather than the subproject dir), which works as follows in your `subproject/build.sbt`:

```
Test/baseDirectory := (ThisBuild/Test/run/baseDirectory).value
```

Maybe this helps someone else. 
