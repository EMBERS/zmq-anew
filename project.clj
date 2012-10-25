(defproject zmq-anew3l "0.1.0"
  :description "ZMQ consumer enhancer for 3 Language ANEW"
  :url ""
  :license {:name ""
            :url ""}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.cli "0.2.2"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.slf4j/slf4j-log4j12 "1.6.4"]
                 [cheshire "4.0.2"]
                 [sentimental "0.1.1"]
                 [sentimental-data "0.1.0"]
                 [wujuko-common "0.1.1"]
                 [org.zeromq/jzmq "1.1.0-SNAPSHOT"]]
  :aliases {"zmqanew" ["trampoline" "run" "zmq-anew3l.core"]}
  :main zmq-anew3l.core
  ;; Note this must match on all systems installed...
  :jvm-opts ["-Djava.library.path=/usr/local/lib/" "-Xmx1024m" "-server"
             "-XX:MaxPermSize=256m"])
