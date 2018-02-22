# examples of http2

Setup
---
This application uses HTTP/2 over TLS for communication. Connections will default to HTTP/1.1 unless an upgrade is requested. In order for this server to correctly handle HTTP/2 connections, a jar for ALPN (Application Layer Protocol Negotiation) will need to be placed on the _boot_ classpath, not the application classpath. The version of this jar is specific to the jre it will run on. The correct ALPN version can be found [here](https://www.eclipse.org/jetty/documentation/current/alpn-chapter.html#alpn-versions). 

A java key and trust store will also need to be provided in the `config.yml` for TLS connections. This can created by the following command (the store will be created in the working directory the command was executed):
 
 ```
    keytool -keystore keystore -alias jetty -genkey -keyalg RSA
 ```
 Follow the prompts to fill out the requested information however you see fit. 

How to start the examples of http2 application
---

1. Run `mvn clean install` to build your application
1. Add the following configs to `config.yml`, and fill in the key/trust store info as necessary
```yaml
server:
  applicationConnectors:
    - type: h2
      port: 8445
      maxConcurrentStreams: 1024
      initialStreamRecvWindow: 65535
      keyStorePath: /Users/sd023192/testingkeystore
      keyStorePassword: password
      trustStorePath: /Users/sd023192/testingkeystore
      trustStorePassword: password

```
3. Start application by specifying the `-Xbootclasspath/p` jvm arg. This is just an example, the ALPN jar can be located anywhere.  
```
java -jar \
-Xbootclasspath/p:/Users/sd023192/.m2/repository/org/mortbay/jetty/alpn/alpn-boot/8.1.4.v20150727/alpn-boot-8.1.4.v20150727.jar \
    http2-server/target/http2-server-1.0-SNAPSHOT.jar server config.yml`
```    

Running Tests
---
The unit tests in `http2-client` also require the `alpn-boot` dependency on the boot classpath of the test. In
order for them to work correctly, the tests that are using HTTP/2 for communication will need to set the same
`-Xbootclasspath/p` as a VM arg for the test configuration. 

This should only be required pre-jdk9, as jdk9 has native support for ALPN. If issues arise with ALPN, then the jar 
can be added. However, `-Xbootclasspath` has been removed from jdk9. Instead use `--patch-module`. See [JDK-8061972](https://bugs.openjdk.java.net/browse/JDK-8061972)
