---
layout: post
title: ! 'Maven: skip GPG sign process'
date: 2012-05-29 07:12:54.000000000 +02:00
permalink: maven-skip-pgp-sign-process
tags: [maven, pgp]
---
While it's generally a good idea to use signed libraries, an enforced sign process in the project definition may get into your way if you fix some open source project. As you don't have the GPG Passphrase you won't be able to install/deploy the artifact into your local/enterprise repository:


```
--- maven-gpg-plugin:1.4:sign (sign-artifacts) @ project-abc ---
GPG Passphrase: *
gpg: no default secret key: secret key not available
gpg: signing failed: secret key not available
```

To get around that you could comment out the <a href="http://maven.apache.org/plugins/maven-gpg-plugin/">maven-gpg-plugin</a> in your pom. But if that's defined some parent pom you don't have that option. In that case you can simply disable it in your pom's <em>project -> build -> plugins</em> section:


``` xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-gpg-plugin</artifactId>
    <configuration>
        <skip>true</skip>
    </configuration>
</plugin>
```
