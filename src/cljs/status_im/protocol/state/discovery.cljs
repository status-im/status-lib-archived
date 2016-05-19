(ns status-im.protocol.state.discovery
  (:require [status-im.protocol.state.storage :as s]
            [status-im.protocol.state.state :as state]
            [status-im.protocol.state.state :as state :refer [set-storage
                                                            set-handler
                                                            set-connection
                                                            set-identity
                                                            connection
                                                            storage]]))

(def discovery-status "discovery-status")
(def discovery-topics "discovery-topics")
(def discovery-hashtags "discovery-hashtags")
(def discovery-name "discovery-name")

(defn save-status [status]
  (let [store (storage)]
    (s/put store discovery-status status)))

(defn get-status []
  (let [store (storage)]
    (s/get store discovery-status)))

(defn save-topics [topics]
  (let [store (storage)]
    (s/put store discovery-topics topics)))

(defn get-topics []
  (let [store (storage)]
    (s/get store discovery-topics)))

(defn save-hashtags [hashtags]
  (let [store (storage)]
    (s/put store discovery-hashtags hashtags)))

(defn get-hashtags []
  (let [store (storage)]
    (s/get store discovery-hashtags)))

(defn save-name [name]
  (let [store (storage)]
    (s/put store discovery-name name)))

(defn get-name []
  (let [store (storage)]
    (s/get store discovery-name)))

