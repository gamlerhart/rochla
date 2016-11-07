{
 :aws-creds {
             ; The AWS access key
             :access-key "AWS-ACCESS-EY"
             ; The AWS secret key
             :secret-key "aws-secret-key"
             ; The AWS region the instances are started / stopped
             :endpoint   "eu-central-1"
             ; The SSH key name the passwords are encrypted with
             :key-name   "general"}
 ; AMI of the windows machine. Expect Windows 2016 Server at the moment
 :ami       "ami-7dfa2e13"
 ; The download location
 :downloads "https://s3-eu-west-1.amazonaws.com/<name>"
 ; URL the app. Needs to be reachable by the windows instances, so they can do api calls
 :rochla    "https://rochla.gamlor.info"
 ; Host and URL the GUAC server
 :guac      {
             :host "localhost"
             :port 4822
             }
 ; Nice, location string, for the UI
 :location "EU, Ireland"
 }