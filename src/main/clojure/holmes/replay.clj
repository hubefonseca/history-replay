(ns holmes.replay
  "History replay functionality."
  (:gen-class)
  (:use clojure.contrib.logging somnium.congomongo)
  (:import [javax.jms] [org.apache.activemq ActiveMQConnectionFactory] [org.apache.activemq.command ActiveMQTextMessage]))


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


(defn send-message
  [obj]
  (let [obj (assoc obj :timestamp (. System currentTimeMillis))
        message (doto (.createMessage @*session*) (.setProperties (zipmap (map name (keys obj)) (vals obj))))]
    (.send @*producer* message)))


(defn send-events
  [coll]
  (let [delay (int (/ 15000 (count (collections))))]
    (loop [skip 0]
      (let [batch (map #(dissoc % :_id :_ns) (fetch coll :limit batch-size :skip skip))]
        (doseq [event batch]
          (. Thread (sleep delay))
          (send-message event))
        (if (> (count batch) 0) (recur (+ skip batch-size)) (recur 0))))))


(defn -main
  []
  (do (start-connection)
    (println "Processing...")
    (doseq [coll (collections)]
      (future (send-events coll)))))

