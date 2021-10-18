# Tiger-Standalone-Proxy

A tool to give you flexible Routing from a to b and to protocol the traffic seen by the proxy.
This version can be booted by itself, either as a jar or as a docker-container.

The standalone Tiger-Proxy has two parts: A Proxy-Server (the Tiger-Proxy itself) and a 
Spring-Boot server (which can be used to configure the proxy, to view the traffic and to
send/receive traffic from other Tiger-Proxies). Consequently it has two ports 
(`server.port` for the Spring-Boot server and `tigerProxy.port` for the proxy-server).

## General configuration

Configuration can be done via an 'application.yml'-file or via CLI-Options.
See https://docs.spring.io/spring-boot/docs/1.4.1.RELEASE/reference/html/boot-features-external-config.htm
for more details.

## Spring-Boot server

* The port can be configured via 'server.port'
* Under 'http://<server>/webui' the traffic which has been routed via this proxy can be viewed (and saved, uploaded and reset)
* Under '/tracing/info' lies a WebSocket/STOMP endpoint which propagates all received traffic. 
This can be used to forward traffic to other tiger-proxies
* Under '/route' resides a REST-Controller which can manage the routes. 
See 'TigerConfigurationController.java' and 'TigerRemoteProxyClientTest.java' for additional information

## Tiger-Proxy

The actual proxy. Can be configured to be either a forward- or a reverse-proxy.
Let's look at a sample-configuration:

```YAML
tigerProxy:
    port: 7777 
    # the port under which the server will be booted
    proxyLogLevel: TRACE
    # logLevel of the proxy-server. DBEUG and TRACE will print traffic, so use with care!
    fileSaveInfo:
        writeToFile: true # should the cleartext http-traffic be logged to a file?
        filename: "foobar.tgr" # customize the filename
        clearFileOnBoot: true # default false
    proxyRoutes:
        - from: http://foobar # defines a forward-proxy-route from this server...
          to: https://cryptic.backend/server/with/path # to this server
        - from: "/blub" 
          # reverse proxy-route. http://<tiger-proxy>/blub will be forwarded
          to: "https://another.de/server"
          activateRbelLogging: false 
          # the traffic for this route will NOT be logged (default is true)

    modifications:
    # a list of modifications that will be applied to every proxied request and response
      - condition: "isRequest"
        # a condition that needs to be fullfilled for the modification to be applied (JEXL grammar)
        targetElement: "$.header.user-agent"
        # which element should be targeted?
        replaceWith: "modified user-agent"
        # the replacement string to be filled in. 
        # This modification will replace the entire "user-agent" in all requests
      - condition: "isResponse && $.responseCode == 200"
        targetElement: "$.body"
        name: "body replacement modification"
        # The name of this modification. This can be used to identify, alter or remove this modification
        replaceWith: "{\"another\":{\"node\":{\"path\":\"correctValue\"}}}"
        # This will replace the body of every 200 response completely with the given json-string
        # (This ignores the existing body. For example this could be an XML-body. Content-Type-headers
        # will NOT be set accordingly)
      - targetElement: "$.body"
        regexFilter: "ErrorSeverityType:((Error)|(Warning))"
        # The given regex will be used to target only parts of targeted element.
        replaceWith: "ErrorSeverityType:Error"
        # this modification has no condition, so it will be applied to every request and every response

    forwardToProxy: # can be used if the target-server (to) is behind another proxy
        hostname: 192.168.110.10
        port: 3128
        type: HTTP
    activateForwardAllLogging: false
    # The tiger-proxy will route google.com to google.com even if no route is set.
    # The traffic routed via this "forwardAll"-routing will be logged by default
    # (meaning it will show up in the Rbel-Logs and be fowarded to tracing-clients)
    # This can be deactivated via this flag
    rbelBufferSizeInMb: 1024 
    # Limits the rbel-Buffer to approximately this size.
    # Note: When Rbel-Analysis is done the size WILL vastly exceed this limit!
    disableRbelParsing: true 
    # Disable traffic-analysis by Rbel. This will not impeed proxy-forwarding nor
    # the traffic-endpoints.
    localResources: true
    # This will share the WebUI-Resources (various CSS-files) from the tiger-proxy 
    # locally, thus enabling usage when no internet connection exists

    tls:
      serverRootCa: "certificate.pem;privateKey.pem;PKCS8"
      # Can be used to define a CA-Identity to be used with TLS. The tiger-proxy will
      # generate an identity when queried by a client that matches the configured route.
      # If the client then in turn trusts the CA this solution will provide you with a seamless
      # TLS experience. It however requires access to the private-key of a trusted CA.
      serverIdentity: "certificateAndKeyAndChain.p12;Password"
      # Alternative solution: now all incoming TLS-traffic will be handled using this identity.
      # This might be easier but requires a certificate which is valid for the configured routes
  
      forwardMutualTlsIdentity: "directory/where/another/identityResides.jks;changeit;JKS"
      # This identity will be used as a client-identity for mutual-TLS when forwarding to
      # other servers. The information string can be
      # "my/file/name.p12;p12password" or
      # "p12password;my/file/name.p12" or
      # "cert.pem;key.pkcs8" or
      # "rsaCert.pem;rsaKey.pkcs1" or
      # "key/store.jks;key" or
      # "key/store.jks;key1;key2" or
      # "key/store.jks;jks;key"
      # 
      # Each part can be one of:
      # * filename
      # * password
      # * store-type (accepted are P12, PKCS12, JKS, BKS, PKCS1 and PKCS8)
      domainName: deep.url.of.server.de
      # domain which will be used as the server address in the TLS-certificate
      alternativeNames:
        - localhost
        - 63.54.54.43
        - foo.bar.server.com
      # Alternate names to be added to the TLS-certificate 
      # (localhost and 127.0.0.1 are added by default)

    keyFolders:
    - .
    # the given folders are loaded into RBel for analysis. This is only necessary to decrypt 
    # traffic when analyzing it. It has no effect on the proxy-functions themselves.

    trafficEndpoints:
      - http://another.tiger.proxy:<proxyPort>
    # A list of upstream tiger-proxies. This proxy will try to connect to all given sources to
    # gather traffic via the STOMP-protocol. If any of the given endpoints are not accesible the
    # server will not boot. (fail fast, fail early)
    trafficEndpointConfiguration:
      name: "tigerProxy Tracing Point"
      # the name for the traffic Endpoint. can be any string, which will be
      # displayed at /tracingpoints
```
