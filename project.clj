(defproject acs-dinner-bot "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [io.pedestal/pedestal.service "0.5.2"]
                 [io.pedestal/pedestal.jetty "0.5.2"]
                 [ch.qos.logback/logback-classic "1.1.7" :exclusions [org.slf4j/slf4j-api]]
                 [org.slf4j/jul-to-slf4j "1.7.21"]
                 [org.slf4j/jcl-over-slf4j "1.7.21"]
                 [org.slf4j/log4j-over-slf4j "1.7.21"]
                 [alandipert/enduro "1.2.0"]
                 [instaparse "1.4.5"]
                 [clj-time "0.13.0"]
                 [clj-http "3.4.1"]]
  :main ^:skip-aot acs-dinner-bot.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
