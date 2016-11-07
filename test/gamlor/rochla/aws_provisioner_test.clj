(ns gamlor.rochla.aws-provisioner-test
  (:require [clojure.test :refer :all]
            [gamlor.rochla.machine-park :as p]
            [gamlor.rochla.example-aws-responses :as examples]
            [gamlor.rochla.aws-provisioner :refer :all]
            [gamlor.rochla.machine-park :as p])
  (:import

    (gamlor.rochla.aws_provisioner AwsProvision)))


(deftest aws-tag-format-convertion
  (is (= {:state "booting" :use "consumer"}
         (tags-to-map [{:value "booting", :key "state"} {:value "consumer", :key "use"}])))
  (is (= {}
         (tags-to-map [])))
  (is (= [{:value "booting", :key "state"} {:value "consumer", :key "use"}]
         (map-to-tags {:state "booting" :use "consumer"})))
  (is (= []
         (map-to-tags {})))
  )


(deftest convert-aws-to-simple-representation
  (let [converted (to-machine examples/instance)]
    (is (= "i-4c91cde9" (:id converted)))
    (is (= {:state "booting" :use "consumer"} (:tags converted)))
    (is (= "ec2-52-69-117-15.ap-northeast-1.compute.amazonaws.com" (:url converted)))
    (is (= "running" (:native-state converted)))
    (is (= :running (:state converted)))
    ))


(def test-creds-for-aws nil)

(when test-creds-for-aws
  (deftest can-start-and-stop
    (let [to-test (AwsProvision. test-creds-for-aws)
          empty-start (list-machines to-test)
          start-one (list-machines to-test)
          after-start (list-machines to-test)
          ]
      (is (= [] empty-start))
      (is (= [start-one] after-start))
      (is (:id start-one))
      (is (= "test" (-> start-one :tags :purpose)))
      )))
