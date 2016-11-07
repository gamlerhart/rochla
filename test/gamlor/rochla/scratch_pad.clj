(ns gamlor.rochla.scratch-pad
  (:require [gamlor.rochla.web-server :as web]
            [gamlor.rochla.pages :as pg]
            [gamlor.rochla.guac-tunnel :as guac]))

(comment


  (require '[gamlor.rochla.machine-park :as p])
  (require '[gamlor.rochla.aws-provisioner :as aws])
  (require '[amazonica.aws.ec2 :as ec2])
  (require '[gamlor.rochla.testing-tools :as tt])
  (require '[gamlor.rochla.web-server :as web])
  (require '[gamlor.rochla.pages :as pg])
  (require '[gamlor.rochla.winbox :as m])
  (require '[gamlor.rochla.guac-tunnel :as guac])

  (def config (m/load-config))
  (def ctrl (m/start-aws config))
  (def app (m/create-app config ctrl))

  (def machine (p/new-machine-info config))
  (def cred (:aws-creds config))

  (def server (atom nil))


  (swap! server
         (fn [s]
           (when s
             (.stop s))
           (web/start-server 8088
                             (pg/app-routes app)
                             (guac/guac-tunnel app)
                             (guac/guac-http-tunnel app))
           ))

  (def tm (p/reserve-machine app))
  (p/ready-to-use app (:id tm))

  )