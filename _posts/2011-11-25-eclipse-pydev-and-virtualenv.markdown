---
layout: post
title: Eclipse, PyDev and virtualenv
date: 2011-11-25 04:19:47.000000000 +01:00
permalink: maven-skip-pgp-sign-process
tag:
  - eclipse
  - python
---
On my current Django project we just moved away from using system-wide installed dependencies to using virtualenv. The transition is pretty straight forward and there's a couple of good how-tos like <a href="http://www.saltycrane.com/blog/2009/05/notes-using-pip-and-virtualenv-django/">this one</a>. Out of the <a href="http://iamzed.com/2009/05/07/a-primer-on-virtualenv/">many good reasons</a> to use virtualenv rather than installing the dependencies system-wide, I found those are the most important: 
<ul>
	<li>projects with different dependencies are completely isolated from each other, no bad surprises if you install a new dependency for some other project</li>
	<li>no bad surprises if your os package manager decides to update a dependency</li>
	<li>dependencies can be defined within the project in a <a href="http://www.saltycrane.com/blog/2009/05/notes-using-pip-and-virtualenv-django/#pip-requirements">requirements file</a></li>
	<li>ability to quickly switch between different versions of dependencies</li>
</ul>
 
If you use the great Eclipse plugin <a href="http://pydev.org/">PyDev</a> as your IDE, here's some simple steps to configure your project to use the virtual environment - assuming you've set it up already:
<ul>
	<li>In your virtual environment there's a python wrapper that we want to use in Eclipse. Go to Window -> Preferences -> Interpreter Python and <strong>create a new interpreter</strong>. Select the python wrapper from the virtual environment ($WORKON_HOME/YOUR_PROJECT/bin/python). The libraries should be selected automatically - leave them as they are. </li>
	<li>In the <strong>project properties</strong> change the interpreter to use the newly created one (Project -> Properties -> PyDev Python Interpreter)</li>
	<li>Alternatively you can manage the environment individually for each <strong>run configuration</strong> in the 'Run -> Run configurations -> Interpreter' tab</li>
</ul>

This worked for me with Eclipse Helios SR2 and PyDev 1.6.5. To test it you could uninstall one of your dependencies from your system and install it only into that virtual environment. Some credit for the steps described goes to <a href="http://lukeplant.me.uk/blog/posts/eclipse-pydev-and-virtualenv/">Luke Plant</a>, however I tried to simplify it a bit. 
