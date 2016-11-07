(ns gamlor.rochla.winbox
  (:gen-class)
  (:require
    [gamlor.rochla.web-server :as srv]
    [gamlor.rochla.pages :as pg]
    [gamlor.rochla.guac-tunnel :as guac]
    [gamlor.rochla.aws-provisioner :as aws]
    [gamlor.rochla.web-server :as web]
    [gamlor.rochla.machine-park :as p]
    [clojure.java.io :as io]
    [clojure.tools.logging :as log])
  (:import (java.util.concurrent Executors TimeUnit)))

(defn- key-or-env [cfg keys env]
  (get-in cfg keys (System/getenv env))
  )

(defn env-config [cfg]
  {
   :aws-creds {:access-key (key-or-env cfg [:aws-creds :access-key] "AWS_ACCESS_KEY")
               :secret-key (key-or-env cfg [:aws-creds :secret-key] "AWS_SECRET_KEY")
               :endpoint   (key-or-env cfg [:aws-creds :endpoint] "AWS_ENDPOINT")
               :key-name   (key-or-env cfg [:aws-creds :key-name] "AWS_KEYNAME")}
   :ami       (key-or-env cfg [:ami] "AWS_AMI")
   :downloads (key-or-env cfg [:downloads] "DOWNLOADS_URL")
   :rochla    (key-or-env cfg [:rochla] "MAIN_URL")
   :guac      {
               :host (key-or-env cfg [:guac :host] "GUAC_HOST")
               :port (get-in cfg [:guac :port]
                             (int
                               (or
                                 (some-> (System/getenv "GUAC_PORT")
                                         Integer/parseInt)
                                 4822)))
               }

   :location    (key-or-env cfg [:location] "LOCATION")
   }

  )

(defn load-config []
  (let [base-cfg (if (.exists (io/file "config.clj"))
                   (load-file "config.clj")
                   {})]
    base-cfg
    )
  )

(defn load-or-env-config []
  (env-config (load-config)))

(defn start-aws [config]
  (let [cred (:aws-creds config)]
    (aws/setup-security-group cred)
    (aws/terminate-all cred)
    (aws/->AwsProvision config)
    ))

(defn create-app [config provisioner]
  {
   :machine-park (atom {})
   :controller   provisioner
   :config       config
   }
  )


(defn start-app-server [app]
  (let [server
        (web/start-server 8080
                          (pg/app-routes app)
                          (guac/guac-tunnel app)
                          (guac/guac-http-tunnel app))
        ]
    (while (= 0 (.available (System/in)))
      (Thread/sleep 100))
    (.stop server)))



(defn -main
  []
  (let [
        scheduler (Executors/newScheduledThreadPool 1)
        config (load-or-env-config)
        aws (start-aws config)
        app (create-app config aws)
        ]
    (log/info "Start with config " config)
    (.schedule scheduler ^Runnable (fn [] (p/terminate-old-boxes app)) 10 TimeUnit/MINUTES)
    (p/reserve-machine app)
    (start-app-server app))
  )
