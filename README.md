red5-plugins
============

Various red5 server plugins.

Build from Source
-----------------

To build a plugin jar, execute the following on the command line from within the plugin base directory:
```
mvn -Dmaven.test.skip=true package
```
This will create the jar in the "target" directory of the workspace; this will also skip the unit tests.

To download the projects dependencies execute this:
```
mvn dependency:copy-dependencies
```
This will download all the dependencies into the "target" directory under "dependency". The files located in that directory should be placed in the red5/plugins directory ONLY if they don't already exist within the red5/lib directory.


Eclipse
----------

1. Create the eclipse project files, execute this within a plugin base directory.
```
mvn eclipse:eclipse
```
2. Import the project into Eclipse.
3. Access the right-click menu and select "Configure" and then "Convert to Maven Project".
4. Now the project will build automatically, if you have the maven plugin installed.

If you see this Warning in eclipse:
```
Build path specifies execution environment JavaSE-1.6. There are no JREs installed in the workspace that are strictly compatible with this environment.
```
Go to the project properties and change the "JRE System Library" to workspace default.

