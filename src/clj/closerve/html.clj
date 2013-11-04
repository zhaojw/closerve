(ns closerve.html
  (:require [hickory.core :as h]
            [clojure.zip :as zip])
  (:use [clojure.xml] 
        [clojure.walk]        
        [clojure.pprint]
        [clojure.contrib.core]
        [hickory zip render]))

(defn locate-in-zip [loc node-fn]
  "search from the start zip location of first node that satisfy node-fn"
  (let [node (zip/node loc)]
    (if (node-fn node) 
      loc
      (let [cnode (zip/down loc)]
        (some #(locate-in-zip % node-fn)
              (take-while #(not (nil? %)) (iterate zip/right cnode))) ))))

(defn insert-into-node [node html-forms node-fn]
  (let [loc (hickory-zip node)
        insert-loc (locate-in-zip loc node-fn)
        new-loc (reduce #(zip/append-child %1 %2) insert-loc html-forms)
        new-node (zip/root new-loc)]
    new-node))
