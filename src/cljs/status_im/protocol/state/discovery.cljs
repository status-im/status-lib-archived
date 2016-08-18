(ns status-im.protocol.state.discovery
  (:require [status-im.protocol.state.storage :as s]
            [status-im.protocol.state.state :as state :refer [set-storage
                                                              set-handler
                                                              set-connection
                                                              connection
                                                              storage]]))

(def discovery-status "discovery-status")
(def discovery-topics "discovery-topics")
(def discovery-hashtags "discovery-hashtags")
(def discovery-name "discovery-name")
(def discovery-photo-path "discovery-photo-path")

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

(defn save-photo-path [photo-path]
  (let [store (storage)]
    (s/put store discovery-photo-path photo-path)))

(defn get-photo-path []
  (let [store (storage)]
    (s/get store discovery-photo-path)))

