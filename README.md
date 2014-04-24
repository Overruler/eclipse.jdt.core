With Improvements, JDT Core and JDT UI
======================================

This and its sister fork contain the additional features in JDT Core and JDT UI that I find useful but are missing from the official Eclipse IDE. These include:

## Ever seen something like this...

The bog standard Eclipse IDE:

![Screenshot showing no suggestions for ArrayList and other subclasses of List](https://raw.github.com/Overruler/eclipse.jdt.core/master/subclasses_old.png)

## And wished it looked more like this

![Screenshot showing all List subclasses suggested in my custom color-theme Eclipse](https://raw.github.com/Overruler/eclipse.jdt.core/master/subclasses_new.png)

## Well...

I can't help with your terrible taste in color themes, but the Content Assist changes are included here!

And there's more!

## More relevant suggestions getting promoted

Content Assist suggests methods from the concrete class before methods from super-classes.

![Screenshot showing Component methods listed before JFrame's](https://raw.github.com/Overruler/eclipse.jdt.core/master/methods_old.png)
![Screenshot showing methods from JFrame suggested first](https://raw.github.com/Overruler/eclipse.jdt.core/master/methods_new.png)

## Refactoring on Steroids

Ctrl-Alt-Left and Ctrl-Alt-Right are like Ctrl-C, Ctrl-V, except smarter.

![Animated GIF recording showing refactoring and inlining local variables](https://raw.github.com/Overruler/eclipse.jdt.core/master/refactoring_2.gif)

# Installing

Clone both JDT Core and JDT UI repositories in Eclipse SDK, import the projects eclipse.jdt.core and eclipse.jdt.ui, make sure the code compiles and export both projects to the current installation.

License
-------

The license is still [Eclipse Public License (EPL) v1.0](http://wiki.eclipse.org/EPL)
