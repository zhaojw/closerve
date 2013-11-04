(ns closerve.snippet
  (:require [hickory.core :as h]
            [clojure.zip :as zip]
            [clojure.set :as set]
            [clojure.core.async :refer [go close! >!! <! >! sliding-buffer chan]])
  (:use [clojure.xml] 
        [clojure.walk]        
        [clojure.contrib.core]
        [hickory zip render]
        [ring.util.time :only (format-date)]
        [ring.util.codec :as codec]
        [closerve state util html wscmd liftutil]))

;;;the call back func of form.ajax will see {name val} dict as cmd
(register-lift-snippet 
   "form.ajax" 
 [node req page-id lift-instr]
 (let [sub-nodes-transed (process-snippets node req page-id)
       {:keys [new-node uuid->name form-id]} (randomize-node-names sub-nodes-transed)
       func-callback nil]
   (register-call-back-fn page-id form-id 
                          (get-in lift-instr [:params "callback"])
                          {:session-id (-> req :session :session-id)
                           :page-id page-id
                           :form-id form-id
                           :name->uuid (set/map-invert uuid->name)}
                          :pre-proc-fn (fn [cm]
                                         (let [data (:data cm)
                                               params (codec/form-decode data "UTF-8")
                                               back-to-orig-name 
                                               (into {} (for [[k v] params] 
                                                          (if-let [orig-name (uuid->name k)]
                                                            [orig-name v] [k v])))]
                                           back-to-orig-name)))
   ;tell the page to bind form-id to send form values back to server
   (send-cmd-to-page page-id {:act :submit :form-id form-id})
   new-node)
)

(register-lift-snippet
 "LazyLoad"
 [node req page-id lift-instr]
 (let [uuid (make-random-uuid)
       fastret (assoc-in node [:attrs :id] uuid)
       fastret (assoc fastret :content ["Loading..."])]
   (go (let [slowret (process-snippets node req page-id)]
         (send-cmd-to-page page-id {:act :replaceWith
                                    :selector (str "#" uuid)
                                    :html (hickory-to-html slowret)})))
   fastret)
 )

(register-lift-snippet
 "comet"
 [node req page-id lift-instr]
 (let [uuid (make-random-uuid)
       actor-fn (@name->comet (get-in lift-instr [:params "type"]))
       comet-ch (chan)
       fastret (assoc node [:attrs :id] uuid)]
   (swap! page->comet-ch assoc-into-key-values page-id comet-ch)
   (if actor-fn (actor-fn comet-ch req page-id uuid))
   fastret))
