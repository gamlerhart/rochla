(defproject gamlor.rochla.winbox "0.1.0-SNAPSHOT"
  :description "Windows machines to play around, in minutes"
  :url "https://rochla.gamlor.info/"
  :license {:name "Mozilla Public License Version 2.0"
            :url  "https://www.mozilla.org/en-US/MPL/2.0/"}
  :source-paths ["src"]
  :java-source-paths ["src"]
  :dependencies [
                 [org.clojure/clojure "1.8.0"]
                 [amazonica "0.3.75"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-servlet "1.4.0"]
                 [org.eclipse.jetty/jetty-server "9.3.13.v20161014"]
                 [org.eclipse.jetty/jetty-servlet "9.3.13.v20161014"]
                 [org.eclipse.jetty.websocket/websocket-server "9.3.13.v20161014"]
                 [compojure "1.4.0"]
                 [enlive "1.1.6"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.glyptodon.guacamole/guacamole-common "0.9.9"]
                 [org.clojure/core.async "0.2.382"]
                 [org.clojure/data.json "0.2.6"]

                 [ch.qos.logback/logback-classic "1.1.7"]   ;; More logging
                 ]

  :main ^:skip-aot gamlor.rochla.winbox
  :target-path "target/%s"
  :uberjar-name "rochla.jar"
  :profiles {:uberjar {:aot :all}
             :dev     {:dependencies [[clj-http "2.2.0"]]}})
