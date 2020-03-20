(ns global-windows.core
  (:require [clojure.core.async :refer [chan >!! <!! close!]]
            [onyx.plugin.kafka]
            [onyx.plugin.core-async :refer [take-segments!]]
            [onyx.api])
  (:gen-class))

(def id (java.util.UUID/randomUUID))

(def env-config
  {:zookeeper/address "127.0.0.1:2188"
   :zookeeper.server/port 2188
   :zookeeper/server? true
   :onyx/tenancy-id id})

(def peer-config
  {:zookeeper/address "127.0.0.1:2188"
   :onyx/tenancy-id id
   :onyx.peer/job-scheduler :onyx.job-scheduler/balanced
   :onyx.peer/storage.zk.insanely-allow-windowing? true
   :onyx.messaging/impl :aeron
   :onyx.messaging/peer-port 40200
   :onyx.messaging/bind-addr "localhost"})

(def batch-size 10)

(def workflow
  [[:in :identity]
   [:identity :out]])

(def catalog
  [{:onyx/name :in
    :onyx/plugin :onyx.plugin.kafka/read-messages
    :onyx/type :input
    :onyx/medium :kafka
    :kafka/topic "my-message-stream"
    :kafka/group-id "onyx-consumer"
    :kafka/zookeeper "127.0.0.1:2181"
    :kafka/offset-reset :latest
    :kafka/deserializer-fn :onyx.tasks.kafka/deserialize-message-edn
    :kafka/wrap-with-metadata? false
    :onyx/max-peers 1
    :onyx/batch-size batch-size
    :onyx/doc "Reads messages from a Kafka topic"}

   {:onyx/name :identity
    :onyx/fn :clojure.core/identity
    :onyx/type :function
    :onyx/batch-size batch-size}

   {:onyx/name :out
    :onyx/plugin :onyx.plugin.core-async/output
    :onyx/type :output
    :onyx/medium :core.async
    :onyx/max-peers 1
    :onyx/batch-size batch-size
    :onyx/doc "Writes segments to a core.async channel"}])

(def capacity 1000)
(def output-chan (chan capacity))

(comment ; input segments to add to a kafka topic outside this process
(def input-segments
  [{:n 0 :event-time #inst "2015-09-13T03:00:00.829-00:00"}
   {:n 1 :event-time #inst "2015-09-13T03:03:00.829-00:00"}
   {:n 2 :event-time #inst "2015-09-13T03:07:00.829-00:00"}
   {:n 3 :event-time #inst "2015-09-13T03:11:00.829-00:00"}
   {:n 4 :event-time #inst "2015-09-13T03:15:00.829-00:00"}
   {:n 5 :event-time #inst "2015-09-13T03:02:00.829-00:00"}]))

(def env (onyx.api/start-env env-config))

(def peer-group (onyx.api/start-peer-group peer-config))

(def n-peers (count (set (mapcat identity workflow))))

(def v-peers (onyx.api/start-peers n-peers peer-group))

(defn inject-out-ch [event lifecycle]
  {:core.async/chan output-chan})

(def out-calls
  {:lifecycle/before-task-start inject-out-ch})

(def lifecycles
  [{:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.kafka/read-messages-calls}
   {:lifecycle/task :out
    :lifecycle/calls ::out-calls}
   {:lifecycle/task :out
    :lifecycle/calls :onyx.plugin.core-async/writer-calls}])

(def windows
  [{:window/id :collect-segments
    :window/task :identity
    :window/type :global
    :window/aggregation :onyx.windowing.aggregation/conj}])

(def triggers
  [{:trigger/window-id :collect-segments
    :trigger/id :sync
    :trigger/on :onyx.triggers/segment
    :trigger/threshold [1 :elements]
    :trigger/sync ::dump-window!}])

(defn dump-window!
  [event window trigger {:keys [lower-bound upper-bound] :as window-data} state]
  (println (format "Window extent [%s - %s] contents: %s"
                   lower-bound upper-bound state)))

(defn -main
  [& args]
  (let [submission (onyx.api/submit-job peer-config
                         {:workflow workflow
                          :catalog catalog
                          :lifecycles lifecycles
                          :windows windows
                          :triggers triggers
                          :task-scheduler :onyx.task-scheduler/balanced})]
    (onyx.api/await-job-completion peer-config (:job-id submission)))

  ;; Sleep until the trigger timer fires.
  (Thread/sleep 5000)

  (let [results (take-segments! output-chan 50)])
  (doseq [v-peer v-peers]
    (onyx.api/shutdown-peer v-peer))

  (onyx.api/shutdown-peer-group peer-group)

  (onyx.api/shutdown-env env))
