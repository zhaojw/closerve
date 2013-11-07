(ns closerve.lift
  (:require [hickory.core :as h]
            [clojure.zip :as zip]
            [ring.util.codec :as codec]
            [ring.util.io :as ring-io])
  (:use [clojure.xml] 
        [clojure.walk]
        [clojure.contrib.core]
        [hickory zip render]
        [ring.util.time :only (format-date)]
        [closerve state util html snippet liftutil])
)


(defn lift-process [orig-resp root-path req]
  (if (nil? orig-resp) nil
      (let [session-id (get-in req [:session :session-id])
            page-id (make-random-uuid)
            _ (do
                (record-page-session page-id session-id)
                (add-page-cmd-chan page-id)
                )
            html-txt (cond (= java.io.File (type (:body orig-resp)))
                           (slurp (.getCanonicalPath (:body orig-resp)))
                           :rest (:body orig-resp))
            orig-form (h/as-hickory (h/parse html-txt))
            new-form (try (process-lift-surround orig-form root-path)
                          (catch Exception e
                            (prn "Error to process lift surround")
                            orig-form))

            new-form (process-snippets new-form req page-id)
            new-form (addin-js new-form req page-id)
            new-html-txt (hickory-to-html new-form)] 
        ;(spit "/tmp/debug.html" new-html-txt)
        ;;;seems must have content-type, otherwise, page will appear blank
        ;(prn (count new-html-txt))
        (-> orig-resp 
            (assoc :body (ring-io/string-input-stream new-html-txt))
            (assoc-in [:headers "Content-Type"] "text/html")
            (assoc-in [:headers "Content-Length"] (str (count (.getBytes new-html-txt "UTF-8"))))
            (assoc-in [:headers "Last-Modified"] (format-date (java.util.Date.)))
            (assoc :status 200))
        )))

(defn wrap-lift [handler]
  "check if there are surround or embed elements in file and convert"
  "then go through individual snippets"
  (fn [req]
    (let [orig-resp (handler req)
          new-resp (lift-process orig-resp @root-dir req)]      
      new-resp)))

