(ns closerve.server
  (:require [ring.util.codec :as codec]
            [ring.util.response :as response]
            [ring.util.request :as request]
            [ring.middleware.session :as session]
            [ring.middleware.head :as head]
            [ring.middleware.file-info :as file-info]
            [ring.middleware.content-type :as content-type]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.resource :as resources]
            [ring.middleware.file :as midfile]
            [ring.middleware.multipart-params :as multi]

            [immutant.web            ]
            [immutant.web.async       :as async]
            [immutant.web.middleware ]
            
            [clojure.core.async :refer [alts! go close! <! >! <!! sliding-buffer chan timeout]]
            [crypto.random :as random]
            [clojure.edn :as edn])
  (:use [closerve state lift util session-manager]
        [clojure.pprint]
        [ring.middleware.session.cookie :only [cookie-store]]
        [ring.middleware.params :only [wrap-params]]))

;;;Acknowledgement: For websocket, a lot is learned from 
;;;https://github.com/uswitch/torus-pong
;;;and some code and patterns are adapted from it as well.
;;;-------  Jiawei Zhao, MKRRF IT Limited

;;;;filexible wrap file middleware
;;;;changed from ring's middleware file.clj implementation
;;;;add index.html to end of path only if path end with '/', not use file-response to do this, since
;;; it seems to mess up relative links for path end with dir name but with no "/" in the end.
(defn try-to-get-file [req & [opts]]
  (let [opts (merge {:root @root-dir, :index-files? false, :allow-symlinks? false} opts)]
    (let [path (get-normalized-path req)
          path (if (= \/ (last path)) (str path "index.html") path)
          path (subs path 1)]
      (if (not= 0 (.indexOf path "template-hidden/"))
        (let [
              t1-response (response/file-response path opts)
              t1-response (if t1-response t1-response 
                              (if 
                                  (= -1 (.indexOf path ".html"))
                                (do 
                                  (response/file-response (str path ".html") opts))
                                nil
                                ))
              ]
          
          t1-response)))))


(defn wrap-flexfile [handler & [opts]]
  "only request with no suffix or request with .html endings will go through lift, excluding static"
  (fn [req]
    (let [path (subs (get-normalized-path req) 1)
          suffix (re-find #"\.([^./]+)$" path)
          get-file-func (if (or (= 0 (.indexOf path "static/"))
                                (and suffix (not (#{".html"} (first suffix)) )))
                     (-> #(try-to-get-file % opts)
                         head/wrap-head
                         file-info/wrap-file-info
                         content-type/wrap-content-type
                         )
                     (-> #(try-to-get-file % opts)
                         wrap-lift
                         ))
          get-result (get-file-func req)]

      (or get-result
          (do (prn (str "use handler func for " path))
              (handler req))))))
;;;;

(defn wrap-enum-sessions [handler & [opts]]
  (fn [req]
    (if (-> req :session :session-id) 
      (do
        ;(prn "session has existing id:" (-> req :session :session-id))
        (handler req))
      (let [session-id (make-new-session @session-manager)]
        ;(prn "new session with id" session-id)
        (-> req 
            (assoc-in [:session :session-id] session-id)
            handler
            (assoc-in [:session :session-id] session-id)
            )))))

(defn wrap-dyna-dispatch [handler & [opts]]
  (fn [req]
    (let [path (get-normalized-path req)
          dis-matches (filter #(re-matches (first %) path) @dyna-dispatch-list)
          dis-rule (first dis-matches)
          mres (if dis-rule (re-matches (first dis-rule) path) nil)
          margs (if (vector? mres) (rest mres) nil)]
      (if dis-rule (apply (second dis-rule) req margs) (handler req))
      ))
  )


(defn wrap-rule-check [handler & [opts]]
  (fn [req]
    (let [path (get-normalized-path req)
          suffix (re-find #"\.([^./]+)$" path)]
      ;(prn "rule check" path suffix)
      (let [matched-rules (filter #(re-matches (first %) path) @access-rules)
            rejected ((comp not empty?) (filter #(not ((second %) req)) matched-rules))]
        (if rejected (@access-deny-action req)
            (handler req))
        ))))

(defn handler [request]
  (if (= "/" (:uri request))
    (response/redirect "/index.html")    
    {:status 404
     :headers {"Content-Type" "text/html"}
     :body "Not Found on Server"}))



(defn process-client-msg [msg]
  "process the msg received from client via ws"
  (let [cmd (edn/read-string msg)]    
    (if-let [callback-uuid (:submit cmd)]
      (if-let [callback-func (@uuid->callback callback-uuid)]
        (callback-func cmd))))
  )

;;ACKNOWLEDGEMENT: this is changed from the immutant/web/middleware.clj, to get the request info for callback
;;for immutant websocket middleware, callbacks
(defn wrap-websocket-with-request
  [handler]
  (fn [request]
    (if (:websocket? request)
      (let [page-id (subs (:uri request) 1)
            ws-callbacks
            {:on-open (fn [channel]                        
                        (if (nil? (@page->session page-id))
                          (do 
                            (prn "bad ws connection request" page-id)
                            (async/send! channel (pr-str {:act :reload}))
                            )
                          (do 
                            (prn "get ws connection from" page-id)
                            (let [cmd-chan (@page->cmd-chan page-id)]    
                              (when cmd-chan 
                                (go                                 
                                 (loop []
                                   (let [[msg c] (alts! [cmd-chan])]
                                     (if msg
                                       (do                  
                                         (condp = c 
                                           cmd-chan (do
                                                      (async/send! channel msg)
                                                      )
                                           )
                                         (recur))))))))               
                            )))
             
             :on-close (fn [channel  {:keys [code reason]}]
                         (remove-page-session page-id))
             
             :on-message (fn [channel msg]                           
                           (do                              
                             (async/send! channel "{}")
                             (process-client-msg msg)))
             }]
        (immutant.web.async/as-channel request ws-callbacks)
        )
      (handler request)
      )

    )
  )



(defn upload-tmp-store [upload-key]
  (println "!!! get uplaod key:" upload-key))

(defn start-server [host port]
  (if (nil? @session-manager) (reset! session-manager 
                                      (->SimpleSessionManager (random/bytes 16)
                                                              (atom nil)
                                                              (atom {}))))
  (init-session-manager @session-manager)

  ; ( -> handler ring-1 ring-2 ...) apply ring-1, ring-2 from inside ring to out
  (let [app (-> handler
                wrap-dyna-dispatch
                wrap-flexfile                
                (resources/wrap-resource "public")
                wrap-params
                (multi/wrap-multipart-params {:store upload-tmp-store})
                wrap-enum-sessions
                wrap-rule-check
                (session/wrap-session {:store (cookie-store {:key (get-cookie-key @session-manager)})})
                wrap-websocket-with-request
                )]
    
    
    (immutant.web/run app {"host" host "port" port })))
