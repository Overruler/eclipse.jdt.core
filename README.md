What's New in This Fork
=======================

This fork contains the JDT Core parts of changes for a few features that I find useful but are missing from the Eclipse IDE. These include:

* Content Assist suggests `ArrayList` as a completion to `List<Object> l = new` as well as other super or subclasses when appropriate.
* Builds nicely as part of eclipse-java8 project for Early Access to Eclipse Java 8.
* Compiles without errors with Java 8 Early Access.
* Compiles without errors with Java 7.

The licence is still [Eclipse Public License (EPL) v1.0][3]

JDT Core
========

This is the core part of Eclipse's Java development tools. It contains the non-UI support for compiling and working with Java code, including the following:

* an incremental or batch Java compiler that can run standalone or as part of the Eclipse IDE
* Java source and class file indexer and search infrastructure
* a Java source code formatter
* APIs for code assist, access to the AST and structured manipulation of Java source.

For more information, refer to the [JDT wiki page] [1] or the [JDT project overview page] [2].

License
-------

[Eclipse Public License (EPL) v1.0][3]

[1]: http://wiki.eclipse.org/JDT_Core
[2]: http://www.eclipse.org/projects/project.php?id=eclipse.jdt.core
[3]: http://wiki.eclipse.org/EPL
