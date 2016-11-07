(ns gamlor.rochla.testing-tools
  (:require [clojure.string :as str]
            [gamlor.rochla.machine-park :as p])
  (:import (java.util UUID)
           (gamlor.rochla.machine_park MachineHerder)))

(def config-no-aws
  {
   :rochla "https://rochla.gamlor.info"
   :downloads "https://s3.ap-northeast-2.amazonaws.com/seoul-storage"
   })

(defn mock-in-memory-controller []
  (reify MachineHerder
    (create-and-setup-machine [this machine]
      {:id (str "i-" (UUID/randomUUID))})
    (start-machine [this machine]
      (assert (str/starts-with? (:id machine) "i-")))
    (update-state-tags [this machine tags]
      (assert (str/starts-with? (:id machine) "i-")))
    (status-machine [this machine]
      (assert (str/starts-with? (:id machine) "i-"))
      {:state :running :url "stuff"}
      )
    (terminate-machine [this machine]
      (assert (str/starts-with? (:id machine) "i-")))

    ))

(def empty-machine-park {})

(defn new-test-app []
  {
   :config       config-no-aws
   :controller   (mock-in-memory-controller)
   :machine-park (atom empty-machine-park)
   })

(defn random-name
  []
  (.toString (UUID/randomUUID)))


