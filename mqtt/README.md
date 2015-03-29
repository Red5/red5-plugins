red5-mqtt-plugin
============

This plugin provides [MQTT](http://mqtt.org/) support services.

A little about MQTT

MQTT is a machine-to-machine (M2M)/"Internet of Things" connectivity protocol. It was designed as an extremely lightweight publish/subscribe messaging transport. It is useful for connections with remote locations where a small code footprint is required and/or network bandwidth is at a premium. For example, it has been used in sensors communicating to a broker via satellite link, over occasional dial-up connections with healthcare providers, and in a range of home automation and small device scenarios. 

Sponsor
-------

Development of this plugin was sponsored by [Seequ](http://seequ.com/).


Special thanks to Andrea and the Moquette group at Eclipse.


Build from Source
-----------------

To build a plugin jar, execute the following on the command line from within the plugin base directory:
```
mvn -Dmaven.test.skip=true -Dmaven.javadoc.skip=true package
```
This will create the jar in the "target" directory of the workspace; this will also skip the unit tests.

To download the projects dependencies execute this:
```
mvn dependency:copy-dependencies
```
This will download all the dependencies into the "target" directory under "dependency". The files located in that directory should be placed in the red5/plugins directory ONLY if they don't already exist within the red5/lib directory.


Deploying to Red5
-----------

Drop the mqttplugin-1.0.jar into red5/plugins and place the following dependencies there as well:
 * Disruptor disruptor-3.3.2.jar
 * MapDb mapdb-1.0.6.jar


Configuration
--------------

Add the MQTT nodes to your red5.xml or use an import. The mqtt.xml file is provided for your convenience.

To bind to one or many IP addresses and ports:
```xml
<bean id="mqttTransport" class="org.red5.server.mqtt.net.MQTTTransport">
        <property name="addresses">
            <list>
            	<value>192.168.1.174</value>
            	<value>192.168.1.174:1883</value>
            	<value>192.168.1.174:2883</value>
            </list>
        </property>
</bean>
```

If you don't want to specify the IP:
```xml
<bean id="mqttTransport" class="org.red5.server.mqtt.net.MQTTTransport">
	<property name="port" value="1883"/>
</bean>

```
To support ssl / tls communication add this:

```xml
    <bean id="mqttTransportSecure" class="org.red5.server.mqtt.net.MQTTTransport">
        <property name="secureConfig">
            <bean id="mqttSecureConfig" class="org.red5.server.mqtt.SecureMQTTConfiguration">
                <property name="keystoreType" value="JKS"/>
                <property name="keystoreFile" value="conf/keystore"/>
                <property name="keystorePassword" value="password"/>
                <property name="truststoreFile" value="conf/truststore"/>
                <property name="truststorePassword" value="password"/>
            </bean>
        </property>
        <property name="addresses">
            <list>
                <value>192.168.1.174:8883</value>
            </list>
        </property>
    </bean>
```

Broker node:

```xml
    <bean id="mqttBroker" class="org.red5.server.mqtt.MQTTBroker" depends-on="mqttTransport">
        <property name="dbStorePath" value="/opt/red5/mqtt_store.mapdb"/>
        <property name="passwdFileName" value=""/>
    </bean>
```

Test Client
-----------

I've provided a [Paho test client here](https://www.dropbox.com/s/avfd5kw7wm9a1vz/mqtt-client.tar.gz?dl=0) for getting up-to-speed quicker.

To start the client:
```
java -jar org.eclipse.paho.mqtt.utility-1.0.2.jar
```

To enable the debug logs:
```
java -Djava.util.logging.config.file=./jsr47min.properties -jar org.eclipse.paho.mqtt.utility-1.0.2.jar
```

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

