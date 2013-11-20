(ns closerve.client
  (:require-macros [cljs.core.async.macros :as m :refer [go]])
  (:require [cljs.core.async :refer [alts! >! <! timeout close! put! chan]]
            [cljs.reader :as reader]
            [goog.events]
            [goog.json]
            [goog.string]
            [goog.net.WebSocket]
            [goog.net.WebSocket.MessageEvent]            
            [goog.net.WebSocket.EventType :as Events]            
            [goog.dom]
            [jayq.core :as jq]
            ))

;;;Acknowledgement: A lot is learned from https://github.com/uswitch/torus-pong
;;;and some code and patterns are adapted from it as well.
;;;-------  Jiawei Zhao, MKRRF IT Limited


;;from torus.pong
(defn log
  [obj]
  (.log js/console (pr-str obj)))

(def host
  (aget js/window "location" "host"))

;;adapted from torus-pong
(defn connect!
  [url]
  (let [ws  (goog.net.WebSocket.)
        in  (chan)
        out (chan)]
    ;(goog.events.listen ws Events/OPENED (fn [e] (log "open") (go (>! out [:opened e]))))
    ;(goog.events.listen ws Events/CLOSED (fn [e] (log "close") (go (>! out [:closed e]))))
    ;(goog.events.listen ws Events/MESSAGE (fn [e] (log "message") (go (>! out [:message (.-message e)]))))
    ;(goog.events.listen ws Events/ERROR (fn [e] (log "error") (go (>! out [:error e]))))
    (.addEventListener ws Events/MESSAGE
                       (fn [e]
                         ;(log "received one message")
                         (put! out [:message (.-message e)])))
    (.addEventListener ws Events/ERROR
                       (fn [e]
                         ;(log "received error message")
                         ))
    (.addEventListener ws Events/OPENED
                       (fn [e]
                         ;(log "received open message")
                         ))
    (.addEventListener ws Events/CLOSED
                       (fn [e]
                         ;(log "received closed message")
                         ))
    (.open ws url)
    (go 
     (loop []
       (if (not (.isOpen ws))
         (do 
           ;(log "not open yet")
           (<! (timeout 1000))
           (recur))))
     (log "connected")
     (.send ws {:msg "hello!"})

     (loop [msg (<! in)]
       (when msg
         (.send ws msg)
         (recur (<! in)))))
    {:in in :out out}))

;;;

(defn process-server-msg [msg in page-id]
  (let [server-cmd (reader/read-string (str msg))]    
    (condp = (server-cmd :act)
      ;;;for reason unknown to me, the :val and :attr method not works
      :val          (.val (jq/$ (:selector server-cmd)) 
                         (:new-val server-cmd))
      :attr         (.attr (jq/$ (:selector server-cmd))
                           (:key server-cmd)
                           (:val server-cmd)
                           )
      ;;;new, in testing still
      :globalEval  (jQuery.globalEval (:code server-cmd))
      :queryEval   (let [qres (js/eval (:code server-cmd))]
                     (go (>! in (str {:act :submit
                                      :page-id page-id
                                      :submit (:uuid server-cmd)
                                      :data  qres}))))
      ;;;
      :reset       (doseq [ef (jq/$ (:selector server-cmd))] (.reset ef))
      :append      (.append (jq/$ (:selector server-cmd)) (:html server-cmd))
      :replaceWith (.replaceWith (jq/$ (:selector server-cmd)) (:html server-cmd))
      :hide        (.hide (jq/$ (:id server-cmd)))
      :submit (let [form-id (str "#" (server-cmd :form-id))]
                (go (loop [matched-form (jq/$ form-id)]
                      (if (= 0 (.size matched-form))
                        (do (<! (timeout 500))
                            (log "wait form element to arrive")
                            (recur (jq/$ form-id)))
                        (.submit matched-form
                         (fn [e]
                           (this-as this 
                                    (.preventDefault e)
                                        ;(log "form submit action")
                                        ;.serialize
                                    (go (>! in (str {:act :submit
                                                     :page-id page-id
                                                     :submit (:form-id server-cmd)
                                                     :data (.serialize (jq/$ this))}))))
                           ))))))
      :redirect   (set! (.-location js/window) (server-cmd :url))
      :reload     (.reload (.-location js/window) true)
      nil          nil ; ignore the none command msgs
      )
    #_(if (not (nil? (server-cmd :act))) ;;; for debugging
      (go (>! in (str {:confirm server-cmd}))))
    )
)

;;;adapted from torus.pong
(defn ^:export run
  [page-id]
  (let [{:keys [in out]} (connect! (str "ws://" host "/" page-id))]

    (go 
     (while true
       (let [[msg c] (alts! [out (timeout 120000)])]
         (if (= c out) 
           (let [[type data] msg]
                 (if (= :message type)
                   (do
                     (process-server-msg data in page-id)
                     )
                   (>! in (str {:error "unknown msg type" } (str data)))))
           (do 
             (>! in (str "{}"))))
         )))
))
