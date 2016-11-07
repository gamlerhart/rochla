(ns gamlor.rochla.machine-park
  "Has the overview of the machine park.
  Core of all the state of this app.
  The app refers to the application state and setup:
  {:config config setup
  :controller controller for the machines
  :machine-park the atom referencing machines
  }"
  (:require [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.util UUID)
           (org.joda.time DateTime)
           (java.time LocalDateTime ZoneId)))

(def max-machines 5)

(def adjectives ["Adorable" "Elegant", "Glamorous", "Mysterious" "Delightful" "Witty" "Brave", "Clever"])
(def animals ["Rabbit", "Koala", "Kiwi", "Dolphin", "Hamster", "Fox", "Ibex", "Otter", "Owl"])
(def verb ["eat", "make", "see", "give", "try", "need", "find"])
(def measure ["bowl", "basket", "plates", "pound", "kilo", "boxes", "crates"])
(def object ["Pasta", "Pancake", "Burgers", "Cake", "Taco"])

(def ^ZoneId utc (ZoneId/of "UTC"))


(defn new-user-name []
  (str (rand-nth adjectives) "-" (rand-nth animals)))

(defn new-password
  "No, this doesn't pass any serious password generation. Tiny, tiny pool of entropy. But it should be FUN.
  It is also put into AWS meta data at the moment, so not a secret. Anyway...it's a throw away machine"
  []
  (str (+ 1 (rand-int 19)) "-" (rand-nth animals) "s-" (rand-nth verb) "-" (+ 1 (rand-int 19)) "-" (rand-nth measure) "-of-" (rand-nth object)))

(def start-instance-script-template (slurp (io/resource "scripts/start-new-instance.template.ps1")))
(def user-init-script-template (slurp (io/resource "scripts/user-init.template.ps1")))
(def user-setup-template (slurp (io/resource "scripts/user-setup.ps1")))

(def subscribe-turbo-snippet (slurp (io/resource "scripts/subscribe-snippet.ps1")))
(def subscription-update-turbo-snippet (slurp (io/resource "scripts/subscription-update-snippet.ps1")))
(def copy-short-cuts-snippet "xcopy $env:APPDATA\"\\Microsoft\\Windows\\Start Menu\\Programs\\Turbo.net\" $env:USERPROFILE\\DESKTOP\\")

(defn script-replace-variables [script variables]
  (reduce
    (fn [s kv]
      (str/replace s (str ":key:" (name (first kv))) (name (second kv)))
      )
    script
    variables
    )
  )

(defn- variables-for-script [config tags]
  (merge tags (select-keys config [:downloads :rochla]))
  )


(defn new-machine-info
  [config]
  (let [user (new-user-name)
        password (new-password)
        id (str (UUID/randomUUID))
        tags {
              :user     user
              :password password
              :id       id
              :state    :created
              }]
    {
     :id             id
     :tags           tags
     :script         (script-replace-variables
                       start-instance-script-template
                       (variables-for-script config tags)
                       )
     :session-script (script-replace-variables
                       user-setup-template
                       (variables-for-script config tags)
                       )
     }
    )
  )


(defn extend-script-with-user-wishes [base-script request]
  (if-let [api-key (:turbo-api-key request)]
    (let [login-script (-> base-script (str "\nturbo login --api-key=" api-key))]
      (cond-> login-script
              (:turbo-subscribe request) (str "\n" subscribe-turbo-snippet)
              (:turbo-installi request) (str "\n"
                                             (str/join "\n"
                                                       (map #(str "turbo installi " %)
                                                            (:turbo-installi request))))
              true (str "\n" copy-short-cuts-snippet)
              (:turbo-subscribe request) (str "\n" subscription-update-turbo-snippet)
              (:turbo-installi request) (str "\n"
                                             (str/join "\n"
                                                       (map #(str "turbo pull " %)
                                                            (:turbo-installi request))))
              )
      )
    base-script
    )
  )


(defprotocol MachineHerder
  (create-and-setup-machine [this machine] "Starts the setup of the given machine.")
  (start-machine [this machine] "Start up machine again")
  (update-state-tags [this machine tags] "")
  (status-machine [this machine] "")
  (terminate-machine [this machine] "")
  )


(defn know-machines [app]
  (vals @(:machine-park app)))


(defn reset-machine-park
  "For testing only"
  [app]
  (reset! (:machine-park app) {})
  )


(defn reserved-machines
  [machines]
  (filter :for-session (vals machines)))


(defn try-phycial-reserve [app machine]
  (let [result (create-and-setup-machine (:controller app) machine)]
    (if (:error result)
      (swap! (:machine-park app) (fn [machines] (dissoc machines (:id machine))))
      (swap! (:machine-park app) (fn [machines] (assoc machines (:id machine)
                                                                (assoc machine
                                                                  :machine result)))))
    (if (:error result) nil machine)))

(defn- find-sessions-machine [machines session]
  (and session (first (filter #(= session (:for-session %)) (vals machines))))
  )


(defn existing-for-session
  "Only return a machine if already exists for given session"
  [app session]
  (let [machines @(:machine-park app)]
    (find-sessions-machine machines session)
    )
  )


(defn- try-reserve-machine [machines machine]
  (let [
        in-use (count (reserved-machines machines))
        all (count machines)]
    (if (and (< all max-machines) (<= all in-use))
      (do
        (conj {(:id machine) machine} machines))
      machines)
    ))


(defn reserve-machine [app]
  "Tries to reserve a machine for later use.
  Returns the machine if could alloate one
  Otherwise returns nil, if there no space or an error occured"
  (let [machine (new-machine-info (:config app))
        machines (swap! (:machine-park app)
                        (fn [machines] (try-reserve-machine machines machine)))]
    (if (get machines (:id machine))
      (try-phycial-reserve app machine)
      nil)
    )
  )


(defn- try-machine-for-session [app session requested-feature]

  (let [machines (swap! (:machine-park app)
                        (fn [machines]
                          (let [free (first (filter #(not (:for-session %)) (vals machines)))
                                now (LocalDateTime/now utc)
                                base-session-script (:session-script free)
                                updated-session-script (extend-script-with-user-wishes
                                                         base-session-script
                                                         requested-feature)
                                allocated (-> free
                                              (assoc :for-session session)
                                              (assoc :reserve-time now)
                                              (assoc :expiration-time (.plusHours now 1))
                                              (assoc :session-script updated-session-script))]
                            (if free
                              (assoc machines (:id allocated) allocated)
                              machines
                              )
                            )
                          ))
        sessions-machine (find-sessions-machine machines session)]
    (when sessions-machine
      (reserve-machine app)
      (update-state-tags (:controller app) (:machine sessions-machine) (:tags sessions-machine)))
    (or sessions-machine {:error :no-machine-left})
    )
  )


(defn machine-for-session
  "Reserve / get a machine for this session.

  requested-feature {
    :turbo-api-key \"log in and enable turbo\"
    :turbo-subscribe true/false
    ::turbo-installi [\"img1\" \"img2\"]
  }
    "
  [app session requested-feature]
  (let [machines @(:machine-park app)]
    (or
      (find-sessions-machine machines session)
      (try-machine-for-session app session requested-feature)
      )
    )
  )

(defn count-machines
  [app]
  (count @(:machine-park app))
  )

(defn machine-for-id
  [app id]
  (-> @(:machine-park app) (get id))
  )

(defn user-init-script [app id]
  (if-let [machine (machine-for-id app id)]
    (script-replace-variables user-init-script-template (variables-for-script (:config app) (:tags machine)))
    nil))

(defn user-current-script [app id]
  (if-let [machine (machine-for-id app id)]
    (if (:session-script machine)
      (script-replace-variables (:session-script machine) (variables-for-script (:config app) (:tags machine)))
      nil)
    nil))

(defn state-for-id
  [app id]
  (-> app (machine-for-id id) :tags :state)
  )
(defn state-for-session [app session]
  (-> @(:machine-park app) (find-sessions-machine session) :tags :state)
  )

(defn change-machine-state
  [app id new-state]
  (swap! (:machine-park app)
         (fn [machines]
           (let [machine (get machines id)]
             (if machine
               (assoc machines (:id machine) (assoc-in machine [:tags :state] new-state))
               machines
               )
             )
           ))
  (let [machine (get @(:machine-park app) id)]
    (if machine
      (do
        (update-state-tags (:controller app) (:machine machine) (:tags machine))
        machine)
      nil
      ))
  )

(defn ready-to-use
  [app id]
  (change-machine-state app id :ready-to-use)
  )

(defn start-for-use
  [app id]
  (change-machine-state app id :started-ready)
  (let [machine (get @(:machine-park app) id)]
    (when machine
      (start-machine (:controller app) (:machine machine))
      ))
  )

(defn status-for-session [app session]
  (let [machine (existing-for-session app session)
        native-status (status-machine (:controller app) (:machine machine))]
    (cond
      (not machine) nil
      (= :created (-> machine :tags :state)) {:session-state :preparing}
      (= :setup-completed (-> machine :tags :state)) {:session-state :preparing}
      (= :ready-to-use (-> machine :tags :state)) (do
                                                    (start-for-use app (:id machine))
                                                    {:session-state :preparing})
      (not (= :running (:state native-status))) (do
                                                  (start-for-use app (:id machine))
                                                  {:session-state :preparing})
      :else (merge {:session-state :running}
                   {:expiration-time (str (:expiration-time machine))}
                   {:location (str (-> app :config :location))}
                   native-status)
      )
    ))

(defn handle-state-transition
  [app id state-signal]
  (let [
        state (state-for-id app id)]
    (if state
      (cond
        (and (= :created state) (= :setup-completed state-signal)) (ready-to-use app id)
        (and (= :setup-completed state) (= :setup-completed state-signal)) ()
        )

      )
    )
  )

(defn terminate-old-boxes [app]
  (let [machines (reserved-machines @(:machine-park app))
        now (LocalDateTime/now utc)
        old-boxes (into [] (filter #(.isBefore (:expiration-time %) now) machines))
        old-ids (map :id old-boxes)]
    (swap! (:machine-park app)
           (fn [machines]
             (apply dissoc (cons machines old-ids))
             ))
    (doseq [o old-boxes]
      (terminate-machine (:controller app) (:machine o)))
    (count old-boxes)
    )
  )
