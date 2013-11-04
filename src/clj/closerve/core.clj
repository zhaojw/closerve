(ns closerve.core
  (:use [closerve state server])  
  (:gen-class))

(defn -main [serverdir & {:strs [host port]
                          :or {host "127.0.0.1" port "8000"}}]
  (reset! root-dir serverdir)
  (start-server host (Integer. port)))


