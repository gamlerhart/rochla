(ns gamlor.rochla.example-aws-responses)

(def instance {:monitoring {:state "disabled"},
               :tags [{:value "booting", :key "state"} {:value "consumer", :key "use"}],
               :root-device-type "ebs",
               :private-dns-name "ip-172-31-4-49.ap-northeast-1.compute.internal",
               :hypervisor "xen",
               :subnet-id "subnet-72a0611a",
               :key-name "aws-key",
               :architecture "x86_64",
               :security-groups [{:group-id "sg-40dbc425", :group-name "launch-wizard-3"}],
               :source-dest-check true,
               :root-device-name "/dev/sda1",
               :virtualization-type "hvm",
               :product-codes [],
               :instance-type "t2.nano",
               :ami-launch-index 0,
               :image-id "ami-936d9d93",
               :state {:name "running", :code 16},
               :state-transition-reason "",
               :network-interfaces [{:description "",
                                     :private-dns-name "ip-172-31-4-49.ap-northeast-1.compute.internal",
                                     :subnet-id "subnet-72a0611a",
                                     :source-dest-check true,
                                     :private-ip-addresses [{:private-ip-address "172.31.4.49",
                                                             :primary true,
                                                             :private-dns-name "ip-172-31-4-49.ap-northeast-1.compute.internal",
                                                             :association {:ip-owner-id "amazon",
                                                                           :public-ip "52.69.117.15",
                                                                           :public-dns-name "ec2-52-69-117-15.ap-northeast-1.compute.amazonaws.com"}}],
                                     :network-interface-id "eni-91e6e4d8",
                                     :vpc-id "vpc-71a06119",
                                     :mac-address "06:ee:20:b2:cf:73",
                                     :association {:ip-owner-id "amazon",
                                                   :public-ip "52.69.117.15",
                                                   :public-dns-name "ec2-52-69-117-15.ap-northeast-1.compute.amazonaws.com"},
                                     :status "in-use",
                                     :private-ip-address "172.31.4.49",
                                     :owner-id "782496940784",
                                     :groups [{:group-id "sg-40dbc425", :group-name "launch-wizard-3"}],
                                     :attachment {
                                                  :status "attached",
                                                  :attachment-id "eni-attach-3d91c326",
                                                  :device-index 0,
                                                  :delete-on-termination true}}],
               :vpc-id "vpc-71a06119",
               :ebs-optimized false,
               :instance-id "i-4c91cde9",
               :public-dns-name "ec2-52-69-117-15.ap-northeast-1.compute.amazonaws.com",
               :private-ip-address "172.31.4.49",
               :placement {:availability-zone "ap-northeast-1b", :group-name "", :tenancy "default"},
               :client-token "ObULi1452920014838",
               :public-ip-address "52.69.117.15",
               :block-device-mappings [{:ebs {
                                              :status "attached",
                                              :volume-id "vol-a459265b",
                                              :delete-on-termination true},
                                        :device-name "/dev/sda1"}]})

(def example-list {:reservations [{:group-names [],
                                :groups [],
                                :instances [],
                                :owner-id "782496940784",
                                :reservation-id "r-6650cd94"}]})