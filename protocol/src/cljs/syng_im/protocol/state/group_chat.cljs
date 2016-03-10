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

(defn add-identity [storage topic identity]
  (let [identities (get-identities storage topic)]
    (when-not (contains? identities identity)
      (->> (conj identities identity)
           (save-identities storage group-id)))))

(defn get-identities [storage topic]
  (let [key (topic-identities-key topic)]
    (s/get storage key)))

(defn get-peer-identities [storage topic]
  (-> (get-identities storage topic)
      (disj (state/my-identity))))

(defn chat-exists? [storage topic]
  (let [key (topic-keypair-key topic)]
    (s/contains-key? storage key)))

(defn get-keypair [storage topic]
  (let [key (topic-keypair-key topic)]
    (s/get storage key)))