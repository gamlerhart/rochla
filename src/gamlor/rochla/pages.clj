(ns gamlor.rochla.pages
  (:gen-class)
  (:require [net.cgrand.enlive-html :as html]
            [gamlor.rochla.machine-park :as p]
            [compojure.core :refer :all]
            [ring.util.codec :as codec]
            [ring.util.response :as rest]
            [clojure.data.json :as json]
            [clojure.string :as str]))

(html/deftemplate site-template-info "site-template-info.html"
                  [content]
                  [:div#main-content] (html/substitute content))

(html/deftemplate site-template-app "site-template-app.html"
                  [content]
                  [:div#main-content] (html/substitute content))

(html/defsnippet welcome-snipped "welcome.html"
                 [:body]
                 []
                 [] ())


(html/defsnippet show-machine "machine.html"
                 [:body]
                 []
                 [] ())

(html/defsnippet existing-machine "existing-machine.html"
                 [:body]
                 []
                 [] ())


(html/defsnippet no-machine-left "no-machine-left.html"
                 [:body]
                 []
                 [] ())

(defn session
  [request]
  (get-in request [:cookies "session" :value]))


(defn parse-form-data
  [request]
  (codec/form-decode (slurp (:body request))))

(defn apply-template
  [template data]
  (reduce str (template data)))

(defn info-page
  [content]
  (apply-template site-template-info content))

(defn app-page
  [content]
  (apply-template site-template-app content))

(defn json-response
  [data]
  (rest/header (-> data json/write-str rest/response) "Content-Type" "applicaition/json;charset=utf-8")
  )

(defn form-to-request [form]

  (let [api-key (get form "api-key")
        subscribe (get form "my-apps")
        images (get form "images-list")]
    (if (str/blank? api-key)
      {}
      {
       :turbo-api-key api-key
       :turbo-subscribe (boolean subscribe)
       :turbo-installi (if (str/blank? images) [] (str/split-lines images))
       }
      )
    )
  )

(def text-content-type "text/plain; charset=\"utf-8\"")

(defn app-routes [app]
  (routes
    (GET "/" []
      (info-page (welcome-snipped)))
    (POST "/create" request
      (let [session (session request)
            form (parse-form-data request)
            old (p/existing-for-session app session )
            machine (p/machine-for-session app session (form-to-request form))]
        (cond
          (= :no-machine-left (:error machine)) (rest/status (rest/response (app-page (no-machine-left))) 503)
          old (app-page (existing-machine))
          :else (rest/redirect "/my-machine")
          )
        ))
    (GET "/create" request
      (rest/redirect "/my-machine"))
    (GET "/my-machine" request
      (let [session (session request)
            existing (p/existing-for-session app session )]
        (if existing
          (app-page (show-machine))
          (rest/redirect "/")
          )
        )
      )

    (context "/api/machines/:machine" [machine]
      (GET "/signal/setup-completed" []
        (if (p/ready-to-use app machine)
          (rest/response "")
          (rest/not-found (str "Could not find machine " machine))
          )
        )
      (GET "/cmd/user-init.ps1" []
        (if-let [script (p/user-init-script app machine)]
          (rest/content-type
            (rest/response script)
            text-content-type)
          (rest/not-found (str "Could not find machine " machine))
          )
        )
      (GET "/cmd/current-user-setup.ps1" []
        (if-let [script (p/user-current-script app machine)]
          (rest/content-type
            (rest/response script)
            text-content-type)
          (rest/not-found (str "Could not find machine " machine))
          )
        )
      )
    (GET "/api/info/location" []
      (rest/response (-> app :config :location))
      )
    (context "/session" []
      (GET "/status" request
        (if-let [session (session request)]
          (if-let [status (p/status-for-session app session)]
            (json-response status)
            (rest/not-found (str "Could not find machine for session " session))
            )
          (rest/not-found (str "No session preset ")))
        )
      )

    ))
