(ns holmes.history-replay
  "Storage module."
  (:gen-class)
  (:use clojure.contrib.logging somnium.congomongo))


(mongo! :db "holmes")
(def batch-size 1000)


(doseq [coll (collections)]
  

  )