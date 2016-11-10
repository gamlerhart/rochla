(ns gamlor.rochla.aws-provisioner
  (:gen-class)
  (:require
    [gamlor.rochla.machine-park :as mp]
    [amazonica.aws.ec2 :as ec2]
    [clojure.tools.logging :as log]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [gamlor.rochla.machine-park :as p])
  (:import
    (gamlor.rochla.machine_park MachineHerder)
    (java.util Base64)
    (java.nio.charset StandardCharsets)
    (org.glyptodon.guacamole.net InetGuacamoleSocket SimpleGuacamoleTunnel)
    (org.glyptodon.guacamole.protocol GuacamoleConfiguration GuacamoleClientInformation ConfiguredGuacamoleSocket)))


(defn tags-to-map
  "Convert the AWS tag format to a regular map"
  [tags]
  (apply merge
         {}
         (map (fn [tag] {(keyword (:key tag)) (:value tag)})
              tags)))

(defn map-to-tags
  "Convert the AWS tag format to a regular map"
  [tags]
  (map (fn [kv] {:key (name (first kv)) :value (name (second kv))}) tags))

(defn to-park-state [aws-machine]
  (let [state (-> aws-machine :state :name)]
    (cond
      (and (= "running" state) (not (str/blank? (:public-dns-name aws-machine)))) :running
      (= "stopping" state) :stopped
      (= "stopping" state) :ready-to-use
      (= "stopped" state) :ready-to-use
      (= "stopped" state) :ready-to-use
      (= "pending" state) :created
      :else :unknown
      ))
  )

(defn to-machine
  "Convert the AWS tag format to a regular map"
  [aws-machine]
  {:id           (:instance-id aws-machine)
   :url          (:public-dns-name aws-machine)
   :native-state (-> aws-machine :state :name),
   :state       (to-park-state aws-machine),
   :tags         (tags-to-map (:tags aws-machine))})


(defn- is-managed
  [instance]
  (let [tags (tags-to-map (instance :tags))]
    (and
      (not= "terminated" (-> instance :state :name))
      (= :managed-windows (keyword (:purpose tags))))))



(defn encode-base-64 [value]
  (.encodeToString (Base64/getEncoder) (.getBytes value StandardCharsets/UTF_8)))


(def allow-rdp {
                :ip-protocol "tcp",
                :to-port     3389,
                :from-port   3389,
                :ip-ranges   ["0.0.0.0/0"]})

(defn setup-security-group
  "Our machines need a security group. We create a win-vpm security group if it does not yet exist"
  [cred]
  (let [existing (map :group-name (:security-groups (ec2/describe-security-groups cred)))]
    (when
      (not-any? #(= "win-vpn" %) existing)
      (ec2/create-security-group cred
                                 {
                                  :group-name  "win-vpn"
                                  :description "Allows Windows RDP through"
                                  })
      (ec2/authorize-security-group-ingress cred (merge {:group-name "win-vpn" :ip-permissions [allow-rdp]})))
    )
  )

(defn create-machine
  "Creates a new machine, initializing it with the given info.

  machine-info has :tags{:id, :user}, :script"
  [cred machine ami]
  (log/info "Starting up new windows box. id " (:id machine))
  (let [user-data (encode-base-64 (str "<powershell>\n" (:script machine) "\n</powershell>"))

        new-instance (ec2/run-instances cred {:image-id        ami
                                              :instance-type   "t2.micro"
                                              :min-count       1
                                              :max-count       1
                                              :key-name        (:key-name cred)
                                              :user-data       user-data
                                              :security-groups ["win-vpn"]})
        id-of-box (-> new-instance :reservation :instances first :instance-id)
        initial-tags {:tags (map-to-tags
                              (merge (:tags machine)
                                     {:purpose :managed-windows})
                              )}]


    (log/info "Created instance " id-of-box)
    (assert (= 1 (count (-> new-instance :reservation :instances))))
    (ec2/create-tags cred
                     (merge {:resources [id-of-box]} initial-tags))

    (let [
          reservations (ec2/describe-instances cred :instance-ids [id-of-box])
          with-dns (-> reservations :reservations first :instances first)
          box (to-machine with-dns)
          ]
      (log/info "EC2 instance created " id-of-box " at " (:url box))
      box
      )))

(defn stop
  [cred machine]
  (ec2/stop-instances cred {:instance-ids [(:id machine)]})
  )

(defn terminate
  [cred machine]
  (ec2/terminate-instances cred {:instance-ids [(:id machine)]})
  )

(defn start
  [cred machine]
  (ec2/start-instances cred {:instance-ids [(:id machine)]})
  )

(defn status
  [cred machine]
  (let [reservations (ec2/describe-instances cred {:instance-ids [(:id machine)]})
        with-dns (-> reservations :reservations first :instances first)]
    (to-machine with-dns)
    )
  )


(defn update-tags [cred machine tags]
  (ec2/create-tags cred {:resources [(:id machine)] :tags (map-to-tags tags)})
  )


(defn list-machines
  [cred]
  (let [raw-data (ec2/describe-instances cred)
        reservations (-> raw-data :reservations flatten)
        instances (flatten (map (fn [i] (i :instances)) reservations))
        valid-instances (filter is-managed instances)
        formatted (map to-machine valid-instances)]
    (println valid-instances)
    (println formatted)
    (into [] formatted)))

(defn terminate-all
  [cred]
  (let [boxes (list-machines cred)
        ids (into [] (map :id boxes))]
    (when (< 0 (count ids))
      (ec2/terminate-instances cred {:instance-ids ids}))
    ))


(defn- do-catch-exception [f & args]
  (try
    (apply f args)
    (catch Exception e
      (do
        (log/error "operation failed " e)
        (:error (.getMessage e))))
    )
  )



(deftype AwsProvision [config]
  MachineHerder
  (p/create-and-setup-machine [this machine]
    (do-catch-exception create-machine (:aws-creds config) machine (:ami config))
    )
  (p/start-machine [this machine] (do-catch-exception start (:aws-creds config) machine))
  (p/update-state-tags [this machine tags] (do-catch-exception update-tags (:aws-creds config) machine tags))
  (p/status-machine [this machine]
    (try
      (status (:aws-creds config) machine)
      (catch Exception e
        (do
          (log/error "operation failed " e)
          (:state :unknown)))
      ))
  (p/terminate-machine [this machine]
    (do-catch-exception terminate (:aws-creds config) machine))

  )
