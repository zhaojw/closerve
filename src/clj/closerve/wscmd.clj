(ns closerve.wscmd
  (:require [clojure.core.async :refer [go close! >!! <! >! sliding-buffer chan]])
  (:use [closerve state util]))

(defn send-cmd-to-page [page-id cmd]
  (if (@page->cmd-chan page-id)
    (go (>! (@page->cmd-chan page-id) (pr-str cmd))))
  )

