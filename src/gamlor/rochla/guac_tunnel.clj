(ns gamlor.rochla.guac-tunnel
  (:require [gamlor.rochla.machine-park :as p]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import (org.glyptodon.guacamole.servlet GuacamoleHTTPTunnelServlet)
           (javax.servlet.http HttpServletRequest)
           (org.glyptodon.guacamole.protocol ConfiguredGuacamoleSocket GuacamoleConfiguration GuacamoleClientInformation GuacamoleStatus)
           (org.glyptodon.guacamole.net SimpleGuacamoleTunnel InetGuacamoleSocket)
           (org.eclipse.jetty.servlet ServletHandler ServletHolder ServletContextHandler)
           (java.net ContentHandler)
           (org.eclipse.jetty.server.handler ContextHandler)
           (org.eclipse.jetty.websocket.api WebSocketAdapter Session UpgradeRequest CloseStatus)
           (org.glyptodon.guacamole GuacamoleException)
           (org.eclipse.jetty.websocket.servlet WebSocketServlet WebSocketServletFactory WebSocketCreator ServletUpgradeRequest ServletUpgradeResponse)
           (gamlor.rochla GuacJettyWebSocket TunnelConnector)
           (java.util.concurrent Executors ThreadFactory)
           (java.util.concurrent.atomic AtomicInteger)))


(defn guac-server [guac-cfg]
  (InetGuacamoleSocket. (-> guac-cfg :host) (-> guac-cfg :port)))

(defn guac-client-info []
  (doto (GuacamoleClientInformation.)
    (.setOptimalScreenWidth 1280)
    (.setOptimalScreenHeight 720)))
(defn guac-rdp [server user password]
  (let [cfg (GuacamoleConfiguration.)]
    (doto cfg
      (.setProtocol "rdp")
      (.setParameter "hostname" server)
      (.setParameter "port" "3389")
      (.setParameter "username" user)
      (.setParameter "password" password)
      (.setParameter "security" "nla")
      (.setParameter "ignore-cert" "true"))
    cfg)
  )
(defn open-tunnel
  [guac machine]
  (let [guac-server (guac-server guac)
        client (guac-client-info)
        rdp (guac-rdp (-> machine :url) (-> machine :tags :user) (-> machine :tags :password))]
    (SimpleGuacamoleTunnel. (ConfiguredGuacamoleSocket. guac-server rdp client))
    )
  )

(defn session-of-request [http-cookies]
  (-> (->> http-cookies (filter #(= "session" (.getName %)))) first .getValue))

(defn create-tunnel [app http-cookies]
  (if-let [session (session-of-request http-cookies)]
    (if-let [machine (p/existing-for-session app session)]
      (open-tunnel (-> app :config :guac)
                   (p/status-machine (:controller app) (:machine machine)))
      nil)
    nil
    )
  )

(defn creator [app]
  (proxy [TunnelConnector] []
    (connect [^Session session]
      (create-tunnel app (.getCookies (.getUpgradeRequest session)))
      ))
  )


(defn socker-handler [app]
  (proxy [WebSocketCreator] []
    (createWebSocket [^ServletUpgradeRequest req ^ServletUpgradeResponse resp]
      (.setHeader resp "Sec-WebSocket-Protocol" (.getHeader req "Sec-WebSocket-Protocol"))
      (GuacJettyWebSocket. (creator app)
                           (Executors/newCachedThreadPool)
                           (session-of-request (.getCookies req)))
      )
    ))



(defn guac-tunnel [app]
  (let [guac-servlet (proxy [WebSocketServlet] []
                       (configure [^WebSocketServletFactory factory]
                         (.setCreator factory (socker-handler app))
                         )
                       )
        servlet-handler (doto (ServletContextHandler.)
                          (.addServlet
                            (ServletHolder. "guac" guac-servlet)
                            "/*"))
        context (doto (ContextHandler.)
                  (.setContextPath "/session/tunnel/")
                  (.setHandler servlet-handler))
        ]
    context
    )
  )
(defn guac-http-tunnel [app]
  (let [guac-servlet (proxy [GuacamoleHTTPTunnelServlet] []
                       (doConnect [^HttpServletRequest request]
                         (create-tunnel app (.getCookies request))
                         ))
        servlet-handler (doto (ServletContextHandler.)
                          (.addServlet
                            (ServletHolder. "guac" guac-servlet)
                            "/*"))
        context (doto (ContextHandler.)
                  (.setContextPath "/session/http-tunnel/")
                  (.setHandler servlet-handler))
        ]
    context
    )
  )