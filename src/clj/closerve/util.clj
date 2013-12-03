(ns closerve.util
  (:require [hickory.core :as h]
            [clojure.zip :as zip]
            [ring.util.request :as request]
            [ring.util.codec :as codec])
  (:use [clojure.xml] 
        [clojure.walk]
        [clojure.contrib.core]
        [hickory zip render])
  (:import java.util.UUID))

(defn make-random-uuid []
  (apply str \F (filter #(not= \- %) (str (UUID/randomUUID)))))

(defn url-params [str-url]
  (let [str-url (codec/url-decode str-url)
        q-pos (.indexOf str-url "?")
        param-str (if (not= -1 q-pos) (subs str-url (inc q-pos)) nil)
        param-parts (if param-str (clojure.string/split param-str #"[;&]"))
        param-pairs (if param-parts (mapcat #(clojure.string/split % #"=") param-parts))
        ]

    (try 
      (if param-pairs (apply hash-map param-pairs) {})
      (catch Exception e
        (prn "Error to parse url-params" param-str)
        {}))))

(defn assoc-into-key-values [inpdic k v]
  "insert value into the set associated with key k in dict"
  (let [orig-val (or (inpdic k) #{})
        new-val (conj orig-val v)]
    (assoc inpdic k new-val)))

(defn dissoc-from-key-values [inpdic k v]
  "remove value from the set associated with key k in dict"
  (let [orig-val (or (inpdic k) #{})
        new-val (disj orig-val v)]
    (assoc inpdic k new-val)))

(defn randomize-node-names [node]
  "change all the name attributes under form node to random uuid and return also a mapping"
  (let [uuid->name (atom {})
        walk-fun (fn [nodex] (if-let [orig-name (get-in nodex [:attrs :name])]
                               (let [new-name (make-random-uuid)]
                                 (swap! uuid->name assoc new-name orig-name)
                                 (assoc-in nodex [:attrs :name] new-name))
                               nodex))
        form-id (make-random-uuid)
        new-node (assoc-in node [:attrs :id] form-id)]
    {:new-node (prewalk walk-fun new-node) :uuid->name @uuid->name :form-id form-id})
  )

;;;mainly used for access rule check
(defn get-normalized-path [req]
  "ring file middleware is case sensitive"
  "change // to /"
  "remove .html"
  "remove index"
  (let [path (codec/url-decode (request/path-info req))
;        path (clojure.string/lower-case path)
        path (if (re-matches #".*[.]html$" path) (subs path 0 (- (.length path) 5)) path)
        path (if (re-matches #".*/index$" path) (subs path 0 (- (.length path) 5)) path)
        path (clojure.string/replace path #"/+" "/")]
    path))
