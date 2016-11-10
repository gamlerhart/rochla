(ns gamlor.rochla.machine-park-tests
  (:require
    [clojure.test :refer :all]
    [gamlor.rochla.machine-park :as p]
    [gamlor.rochla.testing-tools :as tt]
    [clojure.string :as str])
  (:import (java.util UUID)
           (gamlor.rochla.machine_park MachineHerder)
           (java.time LocalDate LocalDateTime)))


(def fail-requests-controller
  (reify MachineHerder
    (create-and-setup-machine [this machine]
      {:error :test-suite-error})
    (start-machine [this machine]
      {:error :test-suite-error})
    (update-state-tags [this machine tags]
      {:error :test-suite-error})
    (terminate-machine [this machine]
      {:error :test-suite-error})

    ))

(defn prepared-machine []
  (let [id (str (UUID/randomUUID))]
    {id {
         :id      id
         :machine (:id (str "i-" (UUID/randomUUID)))
         }})
  )

(defn reserved-test-machine
  ([reserve-time]
   (let [id (str (UUID/randomUUID))]
     {id {
          :id              id
          :for-session     (str (UUID/randomUUID))
          :reserve-time    reserve-time
          :expiration-time (.plusHours reserve-time 1)
          :machine         {:id (str "i-" (UUID/randomUUID))}
          }}))
  ([] (reserved-test-machine (LocalDateTime/now p/utc))))

(def full-machine-park (into {} (repeatedly p/max-machines reserved-test-machine)))


(def ^:dynamic *app* nil)

(defn setup-machine-test-park [park]
  (reset! (:machine-park *app*) park))

(defn controller-config-setup [f]

  (binding [*app* (tt/new-test-app)]
    (f)))


(use-fixtures :each controller-config-setup)

(def session (str (UUID/randomUUID)))
(def session2 (str (UUID/randomUUID)))
(def session3 (str (UUID/randomUUID)))


(deftest password-length-is-below-32
  (let [pwds (repeatedly 100 p/new-password)]
    (is (not (first (filter #(< 64 (count %) ) pwds)))))
  )


(deftest user-name-length-is-below-20
  (let [pwds (repeatedly 100 p/new-user-name)]
    (is (not (first (filter #(< 16 (count %)) pwds)))))
  )

(deftest when-machines-left-give-one-out
  (p/reserve-machine *app*)
  (is (= session (:for-session (p/machine-for-session *app* session {}))))
  )

(deftest remember-giving-out-machine
  (p/reserve-machine *app*)
  (:for-session (p/machine-for-session *app* session {}))
  (is (= session (:for-session (p/existing-for-session *app* session))))
  )
(deftest reserving-a-machine-allocates-new-one
  (p/reserve-machine *app*)
  (:for-session (p/machine-for-session *app* session {}))
  (is (= 2 (p/count-machines *app*)))
  (:for-session (p/machine-for-session *app* session2 {}))
  (is (= 3 (p/count-machines *app*)))
  )
(deftest finding-existing-session-does-not-allocate
  (p/reserve-machine *app*)
  (:for-session (p/machine-for-session *app* session {}))
  (is (= 2 (p/count-machines *app*)))
  (:for-session (p/machine-for-session *app* session {}))
  (is (= 2 (p/count-machines *app*)))
  )
(deftest no-machine-for-session-yet
  (p/reserve-machine *app*)
  (is (nil? (p/existing-for-session *app* session)))
  )
(deftest no-machine-for-session-yet-nil
  (p/reserve-machine *app*)
  (is (nil? (p/existing-for-session *app* nil)))
  )
(deftest when-no-machine-left-error
  (setup-machine-test-park full-machine-park)
  (is (= {:error :no-machine-left} (p/machine-for-session *app* session {}))))
(deftest same-machine-for-same-session
  (p/reserve-machine *app*)
  (is (= (p/machine-for-session *app* session {}) (p/machine-for-session *app* session {})))
  )
(deftest different-machines-for-different-sessions
  (p/reserve-machine *app*)
  (p/reserve-machine *app*)
  (is (not (= (p/machine-for-session *app* session {}) (p/machine-for-session *app* session2 {}))))
  )
(deftest fill-up-session-slots

  (setup-machine-test-park (into {} (drop 2 full-machine-park)))
  (p/reserve-machine *app*)
  (is (= session (:for-session (p/machine-for-session *app* session {}))))
  (p/reserve-machine *app*)
  (is (= session2 (:for-session (p/machine-for-session *app* session2 {}))))
  (p/reserve-machine *app*)
  (is (= {:error :no-machine-left} (p/machine-for-session *app* session3 {})))
  )
(deftest failure-in-creating-machine-removes-it-from-pool
  (binding [*app* (assoc *app* :controller fail-requests-controller)]
    (is (nil? (p/reserve-machine *app*)))
    (is (= 0 (p/count-machines *app*)))

    )
  )

(deftest reserve-1-machine-ahead
  (is (:id (p/reserve-machine *app*)))
  (is (= 1 (p/count-machines *app*))))
(deftest do-not-reserve-machine-if-still-left
  (setup-machine-test-park (into {} (repeatedly 2 prepared-machine)))
  (is (nil? (p/reserve-machine *app*)))
  (is (= 2 (p/count-machines *app*))))
(deftest reserve-up-to-max
  (setup-machine-test-park (rest full-machine-park))
  (is (p/reserve-machine *app*))
  (is (= p/max-machines (p/count-machines *app*)))
  (is (nil? (p/reserve-machine *app*)))
  )
(deftest stop-reserving-at-max
  (setup-machine-test-park full-machine-park)
  (is (nil? (p/reserve-machine *app*)))
  (is (= p/max-machines (p/count-machines *app*)))
  )


(deftest keeps-young-machines-alive

  (setup-machine-test-park (into {} (reserved-test-machine)))
  (is (= 0 (p/terminate-old-boxes *app*)))
  (is (= 1 (p/count-machines *app*))))

(deftest stops-old-machines-keeps-others-alive

  (setup-machine-test-park (into {} [
                                     (reserved-test-machine)
                                     (reserved-test-machine (.minusHours (LocalDateTime/now p/utc) 2))]
                                 ))
  (is (= 1 (p/terminate-old-boxes *app*)))
  (is (= 1 (p/count-machines *app*))))


(deftest machine-has-state

  (p/reserve-machine *app*)
  (let [id (:id (p/machine-for-session *app* session {}))]
    (is (= :created (p/state-for-session *app* session)))))
(deftest can-update-state
  (p/reserve-machine *app*)
  (let [id (:id (p/machine-for-session *app* session {}))]
    (p/change-machine-state *app* id :setup-completed)
    (is (= :setup-completed (p/state-for-id *app* id)))
    ))
(deftest return-updated-machine-otherwise-nil
  (let [existing (:id (p/reserve-machine *app*))
        not-existing (str (UUID/randomUUID))]
    (is (p/change-machine-state *app* existing :setup-completed))
    (is (nil? (p/change-machine-state *app* not-existing :setup-completed)))
    ))

(deftest stop-a-machine
  (let [id (:id (p/reserve-machine *app*))]
    (p/ready-to-use *app* id)
    (is (= :ready-to-use (p/state-for-id *app* id)))
    )
  )
(deftest start-a-machine
  (let [id (:id (p/reserve-machine *app*))]
    (p/ready-to-use *app* id)
    (p/start-for-use *app* id)
    (is (= :started-ready (p/state-for-id *app* id)))
    )
  )

(deftest state-transition-after-initial-install-connect
  (let [id (:id (p/reserve-machine *app*))]
    (p/handle-state-transition *app* id :setup-completed)
    (p/start-for-use *app* id)
    (is (= :started-ready (p/state-for-id *app* id)))
    )
  )


(deftest scripts-are-filled-with-correct-variables
  (are [expected template variables]
    (= expected (p/script-replace-variables template variables))
    "original" "original" {}
    "original" "original" {:some "value"}
    "original-with-variable" "original-with-:key:some" {:some "variable"}
    "original-with-1 and 2" "original-with-:key:1 and :key:2" {:1 "1" :2 "2"}
    )
  )

(deftest builds-script-according-to-user-request
  (are [expected request]
    (= expected (let [r (p/extend-script-with-user-wishes "base-script" request)] r))
    "base-script" {}
    "base-script\nturbo login --api-key=abcde\nxcopy $env:APPDATA\"\\Microsoft\\Windows\\Start Menu\\Programs\\Turbo.net\" $env:USERPROFILE\\DESKTOP\\"
    {:turbo-api-key "abcde"}
    "base-script\nturbo login --api-key=abcde\nxcopy $env:APPDATA\"\\Microsoft\\Windows\\Start Menu\\Programs\\Turbo.net\" $env:USERPROFILE\\DESKTOP\\"
    {:turbo-api-key "abcde" :turbo-subscribe false}
    (str/join "\n"
              ["base-script"
               "turbo login --api-key=abcde"
               "$loginOut = & turbo login 2>&1"
               "$loginName,$rest = $loginOut -split ' ',2"
               "turbo subscribe $loginName"
               "xcopy $env:APPDATA\"\\Microsoft\\Windows\\Start Menu\\Programs\\Turbo.net\" $env:USERPROFILE\\DESKTOP\\"
               "turbo subscription update $loginName"])
    {:turbo-api-key "abcde" :turbo-subscribe true}
    (str/join "\n"
              ["base-script"
               "turbo login --api-key=abcde"
               "turbo installi firefox"
               "turbo installi chrome"
               "xcopy $env:APPDATA\"\\Microsoft\\Windows\\Start Menu\\Programs\\Turbo.net\" $env:USERPROFILE\\DESKTOP\\"
               "turbo pull firefox"
               "turbo pull chrome"])
    {:turbo-api-key "abcde" :turbo-installi ["firefox" "chrome"]}
    (str/join "\n"
              ["base-script"
               "turbo login --api-key=abcde"
               "$loginOut = & turbo login 2>&1"
               "$loginName,$rest = $loginOut -split ' ',2"
               "turbo subscribe $loginName"
               "turbo installi mozilla/firefox"
               "xcopy $env:APPDATA\"\\Microsoft\\Windows\\Start Menu\\Programs\\Turbo.net\" $env:USERPROFILE\\DESKTOP\\"
               "turbo subscription update $loginName"
               "turbo pull mozilla/firefox"])
    {:turbo-api-key "abcde" :turbo-subscribe true :turbo-installi ["mozilla/firefox"]}
    )

  )

(deftest give-machine-user-script
  (p/reserve-machine *app*)
  (is (str/includes? (:session-script (p/machine-for-session *app* session {:turbo-api-key "abcde"}))
                     "turbo login --api-key=abcde"))
  )