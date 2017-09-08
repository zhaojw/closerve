(ns closerve.session-manager
  (:use [closerve util])
  )


(defprotocol SessionManager
  (init-session-manager [this] "init session manager with options")
  (make-new-session [this] "make new session with unique id, return the unique id")
  (get-session-var [this session-id k] "get value of keyword k corresponds to session-id")
  (set-session-var [this session-id k v] "set value of keyword k to value v for session-id")
  (get-cookie-key [this] "get the encry/decry key of cookie store")
  (close-session [this session-id] "close session"))

;a simple in memory session manager implementation

;  session-counter should be (atom nil), session store should be (atom {})
(defrecord SimpleSessionManager [cookie-key session-counter session-store]
  SessionManager
  (init-session-manager [this]
    (reset! session-counter (long (/ (.getTime (java.util.Date.)) 1000))))
  ;; make the session-id more random
  (make-new-session [this] (make-random-uuid) )
  (get-session-var [this session-id k] (get-in @session-store [session-id k]))
  (set-session-var [this session-id k v] (swap! session-store assoc-in [session-id k] v))
  (get-cookie-key [this] cookie-key)
  (close-session [this session-id] (swap! session-store dissoc session-id))
  )
