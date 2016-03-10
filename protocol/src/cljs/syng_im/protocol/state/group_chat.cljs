(ns syng-im.protocol.state.group-chat
  (:require [syng-im.protocol.state.storage :as s]
            [syng-im.protocol.state.state :as state]))

(defn topic-keypair-key [topic]
  (str "group-chat.topic-keypair." topic))

(defn topic-identities-key [topic]
  (str "group-chat.topic-identities." topic))

(defn save-keypair [storage topic keypair]
  (let [key (topic-keypair-key topic)]
    (s/put storage key keypair)))

(defn save-identities [storage topic identities]
  (let [key (topic-identities-key topic)]
    (s/put storage key identities)))

(defn get-identities [storage topic]
  (let [key (topic-identities-key topic)]
    (s/get storage key)))

(defn add-identity [storage topic identity]
  (let [identities (get-identities storage topic)]
    (when-not (contains? identities identity)
      (->> (conj identities identity)
           (save-identities storage topic)))))

(defn get-peer-identities [storage topic]
  (-> (get-identities storage topic)
      (disj (state/my-identity))))

(defn chat-exists? [storage topic]
  (let [key (topic-keypair-key topic)]
    (s/contains-key? storage key)))

(defn get-keypair [storage topic]
  (let [key (topic-keypair-key topic)]
    (s/get storage key)))

(defn remove-group-data [storage topic]
  (let [keypair-key    (topic-keypair-key topic)
        identities-key (topic-identities-key topic)]
    (s/delete storage keypair-key)
    (s/delete storage identities-key)))