(defproject closerve "0.2.0-SNAPSHOT"
  :description "CloServe, a view first web framework"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/clojurescript "0.0-1909"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [ragge/core.async "0.1.0-SNAPSHOT"]
                 [ring "1.2.1"]
                 [com.keminglabs/jetty7-websockets-async "0.1.0"]
                 [jayq "2.4.0"]
                 [crypto-random "1.1.0"]
                 [hickory "0.5.1"]]
  ;:main closerve.core
  :aot :all
  :hooks [leiningen.cljsbuild]
  :source-paths ["src/clj"
                 "src/cljx"]
  :plugins [[lein-cljsbuild "0.3.3"]]
  :cljsbuild {
              :builds
              [{
                :source-paths ["src/cljs"]
                :crossover-path "src/cljx"
                :compiler {:libs ["src/js"]
                           :output-to "resources/public/js/closerve.js"
                           :pretty-print true
                           :optimizations :advanced
                           :externs ["externs/jquery-1.9.js"]
                           }}]})


