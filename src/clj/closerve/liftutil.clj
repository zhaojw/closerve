(ns closerve.liftutil
  (:require [hickory.core :as h]
            [clojure.zip :as zip]
            [ring.util.codec :as codec]
            [ring.util.io :as ring-io])
  (:use [clojure.xml] 
        [clojure.walk]
        [clojure.contrib.core]
        [hickory zip render]
        [ring.util.time :only (format-date)]
        [closerve state util html]))

(defn identify-lift-element [node]
  (let [node-class (get-in node [:attrs :class])
        node-classes (if node-class (clojure.string/split node-class #"[ \n\r\t]+") nil)
        lift-elem (some #(re-matches #"^lift:.*$" %) node-classes)]
    (if lift-elem
      (let [lift-act (first (clojure.string/split (subs lift-elem 5) #"\?"))]
        {:name lift-act :params (url-params lift-elem)})
      nil))
  )

(defn strip-lift-mark [node]
  (let [lift-instr (identify-lift-element node)
        classes-in-lift (get-in lift-instr [:params "class"])
        node-class (str (get-in node [:attrs :class]) " " classes-in-lift)
        orig-classes (if node-class (clojure.string/split node-class #"[ \n\r\t]+") nil)
        new-classes (filter #(not (re-matches #"^lift:.*$" %)) orig-classes)
        new-class (clojure.string/join " " new-classes)]
    (assoc-in node [:attrs :class] new-class)))


(defn find-need-surround-element [loc]
  (let [node (zip/node loc)
        node-class (get-in node [:attrs :class])]
    (if (and node-class 
             (some #(re-matches #"^lift:surround?.*$" %) 
                   (clojure.string/split node-class #"[ \n\r\t]+")))
      loc
      (let [cnode (zip/down loc)]
        (some #(find-need-surround-element %)
              (take-while (comp not nil?)
                          (iterate zip/right cnode)))))))

(defn merge-in-lift-surround [node surround-params root-path]
  (let [node (strip-lift-mark node)
        sfilename (str root-path "/templates-hidden/" (surround-params "with") ".html")
        at-id (surround-params "at")
        shtml (slurp sfilename)
        sform (h/as-hickory (h/parse shtml))
        walk-func (fn [nodex] (if (and (map? nodex)
                                       (= at-id (get-in nodex [:attrs :id])))
                                (do #_(prn "replace node") node) 
                                nodex) )]
    (postwalk walk-func sform)))

(defn process-lift-surround [node root-path]
  (let [node-zip (hickory-zip node)
        surround-elem (find-need-surround-element node-zip)]
    (if surround-elem
      (let [keepnode (zip/node surround-elem)
            node-class (get-in keepnode [:attrs :class])
            lift-code (some #(re-matches #"^lift:surround?.*$" %) 
                            (clojure.string/split node-class #"[ \n\r\t]+"))
            lift-params (url-params lift-code)
            new-node (merge-in-lift-surround keepnode lift-params root-path)]
        (recur new-node root-path))
     node)))



(defn addin-js [node req page-uuid]
  "add in the closerve.js <script> elements to the end of body"
  (let [js-txt (str "<script src='/js/closerve.js'></script>"
                    "<script type='text/javascript'>"
                    #_(format "closerve.client.run('%s');"
                            page-uuid)
                    #_(format "jQuery(document).ready(function() {closerve.client.run('%s');});"
                            page-uuid)
                    (format "$(window).load(function() {closerve.client.run('%s');});"
                            page-uuid)
                    "</script>")
        js-forms (->> js-txt h/parse-fragment (map h/as-hickory))]
    (insert-into-node node js-forms #(= :body (:tag %))))
  )

(defn process-snippets [node req page-id]
  (let [walk-func (fn [nodex]
                    (let [lift-instr (identify-lift-element nodex)]
                      (if lift-instr 
                        (let [snippet-func (@lift-snippet-dict (:name lift-instr))
                              clean-node (strip-lift-mark nodex)]
                          ;(prn lift-instr (:tag nodex) (:class nodex))
                          (if snippet-func 
                            (snippet-func clean-node req page-id lift-instr)
                            clean-node)
                          )
                        nodex)))]
    (prewalk walk-func node))
  )
