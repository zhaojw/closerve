(defproject closerve "0.2.0-SNAPSHOT"
  :description "CloServe, a view first web framework"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.908"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.clojure/core.async "0.4.490"]
                 [ring "1.2.1"]
                 [com.keminglabs/jetty7-websockets-async "0.1.0"]
                 [jayq "2.5.5"]
                 [cljsjs/jquery "3.2.1-0"]
                 [crypto-random "1.1.0"]
                 [hickory "0.5.1"]]
  ;:main closerve.core
  :aot :all
  :hooks [leiningen.cljsbuild]
  :source-paths ["src/clj"
                 ]
  :plugins [[lein-cljsbuild "1.1.7"]
            ]
  
  :cljsbuild {
              :builds
              [{
                :source-paths ["src/cljs"]
                :compiler {
                           :output-to "resources/public/js/closerve.js"
                           :pretty-print true
                           :optimizations :advanced
;;                           :externs ["externs/jquery-3.3.js"]
                           }}]})


