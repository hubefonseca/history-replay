(ns holmes.replay
  "History replay functionality."
  (:gen-class)
  (:use clojure.contrib.logging somnium.congomongo clojure.contrib.duck-streams)
  (:require [org.danlarkin.json :as json])
  (:import [org.apache.activemq ActiveMQConnectionFactory] [org.apache.activemq.util ByteSequence]))


(mongo! :db "holmes")
(def broker "failover:tcp://localhost:61616")
(def batch-size 1000)


(def *producer* (atom {}))
(def *session* (atom {}))


(defn start-connection
  []
  (let [factory (ActiveMQConnectionFactory. broker)
        connection (.createConnection factory)]
    (.start connection)
    (let [session (.createSession connection false, javax.jms.Session/AUTO_ACKNOWLEDGE)
          producer (.createProducer session (.createQueue session "events"))]
      (reset! *session* session)
      (reset! *producer* producer))))


(defn event-type
  [coll]
  (.toUpperCase (subs coll 7)))


(defn send-message
  [coll obj]
  (let [obj (dissoc obj :timestamp)
        message (doto (.createTextMessage @*session*)
      (.setText (json/encode-to-str obj))
      (.setProperties {"eventtype" (event-type coll)})
      )]
    (.send @*producer* message)))


(defn send-events
  [coll]
  (let [delay (int (/ 15000 (count (collections))))]
    (loop [skip 0]
      (let [batch (map #(dissoc % :_id :_ns) (fetch coll :limit batch-size :skip skip))]
        (doseq [event batch]
          (send-message coll event)
          (. Thread (sleep delay)))
        (if (> (count batch) 0) (recur (+ skip batch-size)) (recur 0))))))


(defn -main
  []
  (do (start-connection)
    (println "Processing...")
    (doseq [coll (collections)]
      (cond (not (.contains coll "system.indexes")) (future (send-events coll))))))


;(-main)