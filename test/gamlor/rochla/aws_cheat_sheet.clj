(ns gamlor.rochla.aws-cheat-sheet
  (:require [amazonica.aws.ec2 :as ec2]))



(def allow-rdp {
                :ip-protocol "tcp",
                :to-port 3389,
                :from-port 3389,
                :ip-ranges ["0.0.0.0/0"]})

(def windows-image "ami-58e8c936")

(def cred {:access-key "<access-key>"
           :secret-key "<secret-key>"
           :endpoint   "ap-northeast-1"})
(defn gamlor.rochla.example-aws-responses
  []

  (ec2/describe-instances cred)
  (ec2/describe-security-groups cred)
  (ec2/create-security-group cred {
                                   :group-name "win-vpn"
                                   :description "Allows Windows RDP through"
                                   })
  (ec2/authorize-security-group-ingress cred (merge {:group-name "win-vpn" :ip-permissions [allow-rdp] }))

  (ec2/create-tags cred {:resources ["i-9942f016"] :tags [{:key "state" :value "booting"}]})

  (ec2/run-instances cred {:image-id windows-image
                           :instance-type "m1.small"
                           :min-count 1
                           :max-count 1
                           :key-name "aws-key"
                           :security-groups ["win-vpn"]})
  (ec2/stop-instances cred {:instance-ids ["i-e472d06b"]})
  (ec2/start-instances cred {:instance-ids ["i-e472d06b"]})
  (ec2/terminate-instances cred {:instance-ids ["i-e472d06b"]})
  (ec2/get-password-data cred {:instance-id "i-e472d06b"})
  (ec2/get-password-data cred {:instance-id "i-e472d06b"})
  (defn -main
    "I don't do a whole lot ... yet."
    [& args]
    (println "Hello, World!"))
  )