(ns gamlor.rochla.machine-park-rest-test

  (:require
    [clojure.test :refer :all]
    [gamlor.rochla.testing-tools :as tt]
    [gamlor.rochla.pages :as pages]
    [gamlor.rochla.guac-tunnel :as guac]
    [gamlor.rochla.machine-park :as p]
    [clj-http.client :as http]
    [gamlor.rochla.web-server :as server]
    [clojure.string :as str]
    [clojure.data.json :as json])
  (:import (java.net ServerSocket)
           (java.util UUID)))

(def ^:dynamic *url* nil)
(def ^:dynamic *app* nil)

(defn new-port []
  (let [ss (ServerSocket. 0)
        port (.getLocalPort ss)
        ]
    (.close ss)
    port
    ))

(defn boot-test-server [port app]
  (server/start-server port (pages/app-routes app) (guac/guac-tunnel app) (guac/guac-http-tunnel app)))

(defn controller-config-setup [f]
  (let [port (new-port)
        the-app (tt/new-test-app)
        server (boot-test-server port the-app)]
    (binding [*app* the-app
              *url* (str "http://localhost:" port)]
      (f)
      (.stop server))))


(use-fixtures :each controller-config-setup)

(def no-exeptions {:throw-exceptions false})

(deftest not-existing-machine-404
  (is (= 404 (:status (http/get
                        (str *url* "/api/machines/" (UUID/randomUUID) "/signal/setup-completed")
                        no-exeptions))))
  )

(deftest update-state-for-existing-machine
  (let [id (:id (p/reserve-machine *app*))]
    (is (= 200 (:status (http/get (str *url* "/api/machines/" id "/signal/setup-completed") no-exeptions))))
    (is (= :ready-to-use (p/state-for-id *app* id)))
    )
  )

(deftest initial-user-script-ready
  (let [id (:id (p/reserve-machine *app*))]
    (is (= 200 (:status (http/get (str *url* "/api/machines/" id "/signal/setup-completed") no-exeptions))))
    (let [resp (http/get (str *url* "/api/machines/" id "/cmd/user-init.ps1") no-exeptions)
          content (:body resp)]
      (is (= 200 (:status resp)))
      (is (str/includes? content "$client"))
      (is (not (str/includes? content ":key:rochla")))
      (is (str/includes? content id)))
    )
  )

(deftest initial-script-signals-user-complete
  (let [id (:id (p/reserve-machine *app*))]
    (is (= 200 (:status (http/get (str *url* "/api/machines/" id "/signal/setup-completed") no-exeptions))))
    (let [resp (http/get (str *url* "/api/machines/" id "/cmd/user-init.ps1") no-exeptions)
          content (:body resp)]
      (is (= 200 (:status resp)))
      (is (str/includes? content "$client"))
      (is (str/includes? content "cmd/current-user-setup.ps1"))
      (is (not (str/includes? content ":key:rochla")))
      (is (str/includes? content id)))
    (let [resp (http/get (str *url* "/api/machines/" id "/cmd/current-user-setup.ps1") no-exeptions)]
      (is (= 200 (:status resp))))
    )
  )

(deftest not-existing-session-404
  (let [cs (clj-http.cookies/cookie-store)]
    (http/get (str *url* "/") {:cookie-store cs})
    (is (= 404 (:status (http/get (str *url* "/session/status") no-exeptions))))
    )
  )

(defn get-session-status [cookie-store]

  (let [resp (http/get (str *url* "/session/status") {:cookie-store cookie-store :throw-exceptions false :as :json})
        content (:body resp)]
    (is (= 200 (:status resp)))
    (-> resp :body json/read-str)
    )
  )

(deftest boot-instance-for-session

  (let [
        id (:id (p/reserve-machine *app*))
        cs (clj-http.cookies/cookie-store)
        _ (http/get (str *url* "/") {:cookie-store cs})
        created (http/post (str *url* "/connect") {:form-params {} :cookie-store cs :throw-exceptions false})
        session (get-in created [:cookies "session" :value])
        ]
    (is (= 302 (:status created)))

    (is (= "preparing" (get (get-session-status cs) "session-state")))

    (http/get (str *url* "/api/machines/" id "/signal/setup-completed"))
    (is (= "preparing" (get (get-session-status cs) "session-state")))
    (p/change-machine-state *app* id :running)
    (is (= "running" (get (get-session-status cs) "session-state")))

    )
  )


(defn create-box-request-with-paramers [http-form-params]
  (let [
        id (:id (p/reserve-machine *app*))
        cs (clj-http.cookies/cookie-store)
        _ (http/get (str *url* "/") {:cookie-store cs})
        created (http/post (str *url* "/connect")
                           {:form-params http-form-params :cookie-store cs :throw-exceptions false})]
    (is (is 200 (:status created)))
    id)
  )


(deftest user-script-sets-up-basics

  (let [
        id (create-box-request-with-paramers {})
        ]

    (let [resp (http/get (str *url* "/api/machines/" id "/cmd/current-user-setup.ps1") no-exeptions)
          script (:body resp)]
      (is (str/includes? script "c:\\instata\\wallpaper.jpg")))

    )
  )

(deftest sets-up-turbo
  (let [
        id (create-box-request-with-paramers {"api-key" "test-key"})
        ]
    (prn *url*)
    (let [resp (http/get (str *url* "/api/machines/" id "/cmd/current-user-setup.ps1") no-exeptions)
          script (:body resp)]
      (is (str/includes? script "c:\\instata\\wallpaper.jpg"))
      (is (str/includes? script "turbo login --api-key=test-key")))
    )
  )

(deftest converts-to-request
  (is (= {:turbo-api-key "my-key-and-stuff" :turbo-subscribe true :turbo-installi ["mozilla/firefox"]}
         (pages/form-to-request {"api-key" "my-key-and-stuff", "my-apps" "my-apps", "images-list" "mozilla/firefox"})))
  (is (= {:turbo-api-key "my-key-and-stuff" :turbo-subscribe false :turbo-installi ["mozilla/firefox" "oh/cool"]}
         (pages/form-to-request {"api-key" "my-key-and-stuff", "images-list" "mozilla/firefox\r\noh/cool"})))
  (is (= {}
         (pages/form-to-request {"api-key" "", "images-list" "mozilla/firefox\r\noh/cool"})))
  (is (= {:turbo-api-key "my-key-and-stuff" :turbo-subscribe false :turbo-installi []}
         (pages/form-to-request {"api-key" "my-key-and-stuff"})))
  )