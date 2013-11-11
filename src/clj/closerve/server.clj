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
            [com.keminglabs.jetty7-websockets-async.core :as ws]
            [clojure.core.async :refer [go close! <! >! <!! sliding-buffer chan timeout]]
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
(defn try-to-get-file [req & [opts]]
  (let [opts (merge {:root @root-dir, :index-files? true, :allow-symlinks? false} opts)]
    (let [path (subs (codec/url-decode (request/path-info req)) 1)
          path (if (= \/ (last path)) (str path "index.html") path)]
      (if (not= 0 (.indexOf path "template-hidden/"))
        (let [
              t1-response (response/file-response path opts)
              t1-response (if t1-response t1-response 
                              (if (and (not= 0 (.indexOf path "static/"))
                                       (= -1 (.indexOf path ".html")))
                                (do 
                                  (response/file-response (str path ".html") opts))
                                nil
                                ))]
          
          t1-response)))))


(defn wrap-flexfile [handler & [opts]]
  "only request with no suffix or request with .html endings will go through lift, excluding static"
  (fn [req]
    (let [path (subs (codec/url-decode (request/path-info req)) 1)
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
          (do (prn (str "use handler func for " path)) (handler req))))))
;;;;

(defn wrap-resource-add-type [handler root-path]
  (fn [req]
    (or ((content-type/wrap-content-type 
          (head/wrap-head #(resources/resource-request % root-path))) req)
        (handler req))))


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
        (if rejected (do 
                       (prn "deny access to:" path)
                       {:status 404
                        :headers {"Content-Type" "text/html"}
                        :body "Not Found on Server"})
            (handler req))
        ))))

(defn handler [request]
  (if (= "/" (:uri request))
    (response/redirect "/index.html")    
    {:status 404
     :headers {"Content-Type" "text/html"}
     :body "Not Found on Server"}))

;;;;
(def ws-configurator
  (ws/configurator ws-chan {:path "/"}))

(defn process-client-msg [msg]
  "process the msg received from client via ws"
  (let [cmd (edn/read-string msg)]
    (prn "processing cmd" cmd)
    (if-let [callback-uuid (:submit cmd)]
      (if-let [callback-func (@uuid->callback callback-uuid)]
        (callback-func cmd))))
  )

(defn talk-to-ws-conn [in out page-id]
  (let [cmd-chan (@page->cmd-chan page-id)]    
    (when cmd-chan 
      (go
       (prn "confirm connection msg:" (<! out))
       (loop []
            (let [[msg c] (alts! [cmd-chan out])]
              (if msg
                (do                  
                  (condp = c 
                    cmd-chan (do 
                               (prn "debug:" page-id msg)
                               (>! in msg)
                               )
                    out (do (prn "msg from out channel" msg)
                            (>! in "{}")
                            (process-client-msg msg)
                            ))
                  (recur)))))
          (prn "client disconnected" page-id)
          (remove-page-session page-id)
          ))))

(defn make-new-ws-conn
  []
  (go 
   (loop []
     (let [{:keys [request in out] :as chconn} (<! ws-chan)]
       (when chconn
         (let [page-id (subs (:uri request) 1)]              
           (if (nil? (@page->session page-id))
             (do 
               (prn "bad ws connection request" page-id)
               ;(>! in (pr-str {:act :redirect :url "/"}))
               (>! in (pr-str {:act :reload}))
               )
             (do 
               (prn "new ws connection" page-id)
               ;(pprint request)
               (talk-to-ws-conn in out page-id))))))
     (recur)))
  )
  

(defn start-server [host port]
  (if (nil? @session-manager) (reset! session-manager 
                                      (->SimpleSessionManager (random/bytes 16)
                                                              (atom nil)
                                                              (atom {}))))
  (init-session-manager @session-manager)

  (let [app (-> handler
                wrap-dyna-dispatch
                wrap-flexfile
                (wrap-resource-add-type "public/")
                wrap-params
                wrap-enum-sessions
                (session/wrap-session {:store (cookie-store {:key (get-cookie-key @session-manager)})})
                wrap-rule-check
                )]
    
    (make-new-ws-conn)
    (jetty/run-jetty app {:host host :port port :join? false 
                          :configurator ws-configurator})))
