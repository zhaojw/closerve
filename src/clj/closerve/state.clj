(ns closerve.state
  (:use [ring.util.codec :only (assoc-conj)]
        [closerve util])
  (:require  [clojure.core.async :refer [go close! <! >! sliding-buffer chan]]
             [ring.util.response :as response]))

(defonce root-dir (atom nil))
(defonce access-deny-action (atom (fn [req]
                                    (do 
                                      (prn "deny access to:" (get-normalized-path req))
                                      {:status 404
                                       :headers {"Content-Type" "text/html"}
                                       :body "Not Found on Server"}))))
(defonce ws-chan (chan))
(defonce access-rules (atom [[#"^/\..*$" (fn [req] false)]
                             [#"^.*~$" (fn [req] false)]
                             [#"^/templates-hidden.*$" (fn [req] false)]
                             ]))

(defonce session-manager (atom nil))

;;;for lift snippet->func association
(defonce lift-snippet-dict (atom {}))
(defmacro register-lift-snippet [path args & body]
  `(swap! lift-snippet-dict assoc ~path (fn [~@args] ~@body)))

;;;for call back funcs
;maps to processing function
(defonce name->callback (atom {}))
;maps to call back func
(defonce uuid->callback (atom {}))
;map to record the callback uuids for a page
(defonce page->uuid (atom {}))

(defn make-call-back-fn [fn-name context pre-proc-fn post-proc-fn]
  (fn [cmd]
    ;(prn "callback fn invoked")
    (if-let [proc-fn (@name->callback fn-name)]
      (post-proc-fn 
       (proc-fn context (pre-proc-fn cmd))))
    ))

;;for ajax.form proc
(defmacro register-cmd-proc-fn [fn-name args & body]
  `(swap! name->callback assoc ~fn-name (fn [~@args] ~@body)))


;pre-proc-fn is to help user write call back func for form.ajax, etc, with prepared data as innput
;so avoid a lot ceremony for the user
(defn register-call-back-fn [page-id uuid fn-name context
                             & {:keys [pre-proc-fn post-proc-fn] 
                                :or {pre-proc-fn (fn [cm] cm)
                                     post-proc-fn (fn [res] nil)}}]
  (let [cbfn (make-call-back-fn fn-name context pre-proc-fn post-proc-fn)]
    (swap! uuid->callback assoc uuid cbfn)
    (swap! page->uuid assoc-into-key-values page-id uuid))
  )

;;;
(defonce name->comet (atom {}))
(defonce page->comet-ch (atom {}))

(defmacro register-comet-fn [fn-name args & body]
  `(swap! name->comet assoc ~fn-name (fn [~@args] (go ~@body))))


;;;for dispatch request, REST way
(defonce dyna-dispatch-list (atom []))

(defn add-dyna-action [path process-fn]
  (swap! dyna-dispatch-list conj [path process-fn]))

(defmacro register-dyna-action [path args & body]
  `(swap! dyna-dispatch-list conj [~path (fn [~@args] ~@body)]))


;;;for record page-uuid, session-id
(defonce page->session (atom {}))

(defonce session->page (atom {}))

(defn record-page-session [page-uuid session-id]
  (swap! page->session assoc page-uuid session-id)
  (swap! session->page assoc-into-key-values session-id page-uuid))


;;;ws commands channel page-uuid -> channel
(defonce page->cmd-chan (atom {}))

(defn add-page-cmd-chan [page-id]
  (let [c (chan)]
    (swap! page->cmd-chan assoc page-id c)
    ))


(defn remove-page-session [page-uuid]
  (let [session-id (@page->session page-uuid)
        cmd-chan (@page->cmd-chan page-uuid)]
    (when cmd-chan (close! cmd-chan))
    (swap! page->cmd-chan dissoc page-uuid)
    (swap! page->session dissoc page-uuid)
    (swap! session->page dissoc-from-key-values session-id page-uuid)
    (doseq [uuid (@page->uuid page-uuid)]
      (swap! uuid->callback dissoc uuid))
    (doseq [comet-ch (@page->comet-ch page-uuid)]
      (close! comet-ch))
    (swap! page->uuid dissoc page-uuid)
    ))

;; need to clean session if it goes to inactive for certain long time
