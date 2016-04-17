(ns syng-im.protocol.state.discovery
  (:require [syng-im.protocol.state.storage :as s]
            [syng-im.protocol.state.state :as state]
            [syng-im.protocol.state.state :as state :refer [set-storage
                                                            set-handler
                                                            set-connection
                                                            set-identity
                                                            connection
                                                            storage]]))

(def discovery-status "discovery-status")
(def discovery-topics "discovery-topics")

(defn save-status [status]
  (let [store (storage)]
    (s/put store discovery-status status)))

(defn get-status []
  (let [store (storage)]
    (s/get store discovery-status)))


(defn save-topics [topics]
  (let [store (storage)]
    (s/put store discovery-topics (clojure.string/join ":" topics))))

(defn get-topics[]
  (let [store (storage)]
    (clojure.string/split (s/get store discovery-topics) #":")))