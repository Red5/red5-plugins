Tomcat JEE Container for Red5
==============

This plugin for Red5 server encapsulates and extends [Tomcat](https://tomcat.apache.org/index.html) in an embedded form. In addition to the the JEE container features, the Tomcat WebSocket implementation is also included; a reference implementation of JSR-365. Whenever possible the Tomcat code has been used directly, where its not possible (mostly due to packaging), we have copied the original sources. For instance, many of the classes in `org.red5.net.websocket.server` originate from `org.apache.tomcat.websocket.server`; all for Red5 and Tomcat continue to be licensed under APL 2.0.


## Migration Steps

### Migration from Red5 Tomcat plugin version 1.20

The only new part of the configuration to support WebSocket, is the addition of the property to enable or disable the WebSocket feature within the `tomcat.server` bean.
```xml
    <property name="websocketEnabled" value="true" />
```

### Migration from Red5 WebSocket plugin version 1.16.14 and earlier

The first step is to identify and special configuration in-place within your existing `webSocketTransport` or `webSocketTransportSecure` beans. If you have specified `cipherSuites` or `protocols`, they will need to be translated over to the Tomcat configuration bean. Once you've taken note of your configuration options, remove the `webSocketTransport` or `webSocketTransportSecure` beans in your `conf/jee-container.xml` file.

The IP addresses and ports identified for `ws` and `wss` in the `conf/jee-container.xml` file are no longer used. The `http` and `https` configuration in the Tomcat bean are used instead since this version of the WebSocket plugin is integrated with Tomcat itself. 


## Tomcat Server

Development is based on version 8.5.35 of Tomcat.


## WebSocket

Websocket plug-in is integrated into the Tomcat plugin as of this latest release. The primary reasoning behind this is the maintenance aspect; Tomcat has a team and we do not. This change also means a move away from Mina for the I/O layer for WebSockets; the previous plugin will continue to live on [here](https://github.com/Red5/red5-websocket).

This plugin is meant to provide websocket functionality for applications running in red5. The code is constructed to comply with [rfc6455](http://tools.ietf.org/html/rfc6455) and [JSR365](https://www.oracle.com/technetwork/articles/java/jsr356-1937161.html).



The previous Red5 WebSocket plugin was developed with the of Takahiko Toda and Dhruv Chopra.

## Configuration

Update the `conf/jee-container.xml` file to suit your needs; http/ws or https/wss.

```xml

   <bean id="tomcat.server" class="org.red5.server.tomcat.TomcatLoader" depends-on="context.loader,warDeployer" lazy-init="true">
        <property name="websocketEnabled" value="true" />
        <property name="webappFolder" value="${red5.root}/webapps" />
        <property name="connectors">
            <list>
                <bean name="httpConnector" class="org.red5.server.tomcat.TomcatConnector">
                    <property name="protocol" value="org.apache.coyote.http11.Http11Nio2Protocol" />
                    <property name="address" value="${http.host}:${http.port}" />
                    <property name="redirectPort" value="${https.port}" />
                    <property name="connectionProperties">
                        <map>
                            <entry key="maxHttpHeaderSize" value="${http.max_headers_size}"/>
                            <entry key="maxKeepAliveRequests" value="${http.max_keep_alive_requests}"/>
                            <entry key="keepAliveTimout" value="-1"/>
                        </map>
                    </property>
                </bean>
            </list>
        </property>
        <property name="baseHost">
           <bean class="org.apache.catalina.core.StandardHost">
               <property name="name" value="${http.host}" />
           </bean>
        </property>
        <property name="valves">
            <list>
                <bean id="valve.access" class="org.apache.catalina.valves.AccessLogValve">
                    <property name="directory" value="log" />
                    <property name="prefix" value="${http.host}_access." />
                    <property name="suffix" value=".log" />
                    <property name="pattern" value="common" />
                    <property name="rotatable" value="true" />
                </bean>
            </list>
        </property>
    </bean>

   <bean id="tomcat.server" class="org.red5.server.tomcat.TomcatLoader" depends-on="context.loader" lazy-init="true">
        <property name="websocketEnabled" value="true" />
        <property name="webappFolder" value="${red5.root}/webapps" />
        <property name="connectors">
            <list>
                <bean name="httpConnector" class="org.red5.server.tomcat.TomcatConnector">
                    <property name="protocol" value="org.apache.coyote.http11.Http11Nio2Protocol" />
                    <property name="address" value="${http.host}:${http.port}" />
                    <property name="redirectPort" value="${https.port}" />
                </bean>
                <bean name="httpsConnector" class="org.red5.server.tomcat.TomcatConnector">
                    <property name="secure" value="true" />
                    <property name="protocol" value="org.apache.coyote.http11.Http11Nio2Protocol" />
                    <property name="address" value="${http.host}:${https.port}" />
                    <property name="redirectPort" value="${http.port}" />
                    <property name="connectionProperties">
                        <map>
                            <entry key="port" value="${https.port}" />
                            <entry key="redirectPort" value="${http.port}" />
                            <entry key="SSLEnabled" value="true" />
                            <entry key="sslProtocol" value="TLSv1.2" />
                            <entry key="ciphers" value="TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256,TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,TLS_RSA_WITH_AES_128_GCM_SHA256,TLS_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_128_CBC_SHA" />
                            <entry key="useServerCipherSuitesOrder" value="true" />
                            <entry key="keystoreFile" value="${rtmps.keystorefile}" />
                            <entry key="keystorePass" value="${rtmps.keystorepass}" />
                            <entry key="truststoreFile" value="${rtmps.truststorefile}" />
                            <entry key="truststorePass" value="${rtmps.truststorepass}" />
                            <entry key="clientAuth" value="false" />
                            <entry key="allowUnsafeLegacyRenegotiation" value="false" />
                            <entry key="maxHttpHeaderSize" value="${http.max_headers_size}"/>
                            <entry key="maxKeepAliveRequests" value="${http.max_keep_alive_requests}"/>
                            <entry key="keepAliveTimout" value="-1"/>
                            <entry key="useExecutor" value="true"/>
                            <entry key="maxThreads" value="${http.max_threads}"/>
                            <entry key="acceptorThreadCount" value="${http.acceptor_thread_count}"/>
                            <entry key="processorCache" value="${http.processor_cache}"/>
                        </map>
                    </property>
                </bean>
            </list>
        </property>
        <property name="baseHost">
            <bean class="org.apache.catalina.core.StandardHost">
                <property name="name" value="${http.host}" />
            </bean>
        </property>
    </bean>

```

To bind to one or many IP addresses and ports:

```xml
<bean id="webSocketTransport" class="org.red5.net.websocket.WebSocketTransport">
        <property name="addresses">
            <list>
            	<value>192.168.1.174</value>
            	<value>192.168.1.174:8080</value>
            	<value>192.168.1.174:10080</value>
            </list>
        </property>
</bean>
```

If you don't want to specify the IP:
```
<bean id="webSocketTransport" class="org.red5.net.websocket.WebSocketTransport">
	<property name="port" value="8080"/>
</bean>

```
To support secure communication (wss) add this:

```xml
    <bean id="webSocketTransportSecure" class="org.red5.net.websocket.WebSocketTransport">
        <property name="secureConfig">
            <bean id="webSocketSecureConfig" class="org.red5.net.websocket.SecureWebSocketConfiguration">
                <property name="keystoreFile" value="conf/keystore"/>
                <property name="keystorePassword" value="password"/>
                <property name="truststoreFile" value="conf/truststore"/>
                <property name="truststorePassword" value="password"/>
            </bean>
        </property>
        <property name="addresses">
            <list>
                <value>192.168.1.174:10081</value>
            </list>
        </property>
    </bean>
```
If you are not using unlimited strength JCE (you are outside the US), you may have to specify the cipher suites as shown below:
```xml
    <bean id="webSocketTransportSecure" class="org.red5.net.websocket.WebSocketTransport">
        <property name="secureConfig">
            <bean id="webSocketSecureConfig" class="org.red5.net.websocket.SecureWebSocketConfiguration">
                <property name="keystoreFile" value="conf/keystore"/>
                <property name="keystorePassword" value="password"/>
                <property name="truststoreFile" value="conf/truststore"/>
                <property name="truststorePassword" value="password"/>
                <property name="cipherSuites">
                    <array>
                        <value>TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256</value>
                        <value>TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA</value>
                        <value>TLS_ECDHE_RSA_WITH_RC4_128_SHA</value>
                        <value>TLS_RSA_WITH_AES_128_CBC_SHA256</value>
                        <value>TLS_RSA_WITH_AES_128_CBC_SHA</value>
                        <value>SSL_RSA_WITH_RC4_128_SHA</value>
                    </array>
                </property>
                <property name="protocols">
                    <array>
                        <value>TLSv1</value>
                        <value>TLSv1.1</value>
                        <value>TLSv1.2</value>
                    </array>
                </property>
            </bean>
        </property>
        <property name="addresses">
            <list>
                <value>192.168.1.174:10081</value>
            </list>
        </property>
    </bean>

```


Adding WebSocket to an Application
------------------------

To enable websocket support in your application, add this to your appStart() method:

```
  WebSocketScopeManager manager = ((WebSocketPlugin) PluginRegistry.getPlugin(WebSocketPlugin.NAME)).getManager(scope);
  manager.setApplication(this);
```

For clean-up add this to appStop():

```
  WebSocketScopeManager manager = ((WebSocketPlugin) PluginRegistry.getPlugin(WebSocketPlugin.NAME)).getManager(scope);
  manager.stop();
```

Security Features
-------------------
Since WebSockets don't implement Same Origin Policy (SOP) nor Cross-Origin Resource Sharing (CORS), we've implemented a means to restrict access via configuration using SOP / CORS logic. To configure the security features, edit your `conf/jee-container.xml` file and locate the bean displayed below:
```xml
    <bean id="webSocketTransport" class="org.red5.net.websocket.WebSocketTransport">
        <property name="addresses">
            <list>
                <value>${ws.host}:${ws.port}</value>
            </list>
        </property>
        <property name="sameOriginPolicy" value="false" />
        <property name="crossOriginPolicy" value="true" />
        <property name="allowedOrigins">
            <array>
                <value>localhost</value>
                <value>red5.org</value>
            </array>
        </property>
    </bean>
```
Properties:
 * [sameOriginPolicy](https://www.w3.org/Security/wiki/Same_Origin_Policy) - Enables or disables SOP. The logic differs from standard web SOP by *NOT* enforcing protocol and port.
 * [crossOriginPolicy](https://www.w3.org/Security/wiki/CORS) - Enables or disables CORS. This option pairs with the `allowedOrigins` array.
 * allowedOrigins - The list or host names or fqdn which are to be permitted access. The default if none are specified is `*` which equates to any or all.
 


Test Page
-------------------

Replace the wsUri variable with your applications path.

```
<!DOCTYPE html>  
<meta charset="utf-8" />  
<title>WebSocket Test</title>  
<script language="javascript" type="text/javascript">  
var wsUri = "ws://192.168.1.174:10080/myapp"; 
var output;  function init() { output = document.getElementById("output"); testWebSocket(); }  function testWebSocket() { websocket = new WebSocket(wsUri); websocket.onopen = function(evt) { onOpen(evt) }; websocket.onclose = function(evt) { onClose(evt) }; websocket.onmessage = function(evt) { onMessage(evt) }; websocket.onerror = function(evt) { onError(evt) }; }  function onOpen(evt) { writeToScreen("CONNECTED"); doSend("WebSocket rocks"); }  function onClose(evt) { writeToScreen("DISCONNECTED"); }  function onMessage(evt) { writeToScreen('<span style="color: blue;">RESPONSE: ' + evt.data+'</span>'); websocket.close(); }  function onError(evt) { writeToScreen('<span style="color: red;">ERROR:</span> ' + evt.data); }  function doSend(message) { writeToScreen("SENT: " + message);  websocket.send(message); }  function writeToScreen(message) { var pre = document.createElement("p"); pre.style.wordWrap = "break-word"; pre.innerHTML = message; output.appendChild(pre); }  window.addEventListener("load", init, false);  </script>  <h2>WebSocket Test</h2> <div id="output"></div>
```

Demo application project
----------------
https://github.com/Red5/red5-websocket-chat


Pre-compiled JAR
----------------
You can find [compiled artifacts via Maven](https://mvnrepository.com/artifact/org.red5/tomcatplugin)

