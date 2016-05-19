(ns status-im.protocol.state.group-chat
  (:require [status-im.protocol.state.storage :as s]
            [status-im.protocol.state.state :as state]))

(defn topic-keypair-key [topic]
  (str "group-chat.topic-keypair." topic))

(defn topic-identities-key [topic]
  (str "group-chat.topic-identities." topic))

(defn topic-admin-ident-key [topic]
  (str "group-chat.topic-admin-ident." topic))

(defn topic-group-name-key [topic]
  (str "group-chat.topic-group-name." topic))

(defn save-group-name [storage topic group-name]
  (let [key (topic-group-name-key topic)]
    (s/put storage key group-name)))

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

(defn remove-identity [storage topic identity]
  (let [identities (get-identities storage topic)]
    (when (contains? identities identity)
      (->> (disj identities identity)
           (save-identities storage topic)))))

(defn group-member? [storage topic identity]
  (let [identities (get-identities storage topic)]
    (contains? identities identity)))

(defn get-peer-identities [storage topic]
  (-> (get-identities storage topic)
      (disj (state/my-identity))))

(defn chat-exists? [storage topic]
  (let [key (topic-keypair-key topic)]
    (s/contains-key? storage key)))

(defn get-keypair [storage topic]
  (let [key (topic-keypair-key topic)]
    (s/get storage key)))

(defn group-name [storage topic]
  (let [key (topic-group-name-key topic)]
    (s/get storage key)))

(defn remove-group-data [storage topic]
  (let [keypair-key    (topic-keypair-key topic)
        identities-key (topic-identities-key topic)]
    (s/delete storage keypair-key)
    (s/delete storage identities-key)))

(defn save-group-admin [storage topic identity]
  (let [key (topic-admin-ident-key topic)]
    (s/put storage key identity)))

(defn group-admin? [storage topic identity]
  (let [key (topic-admin-ident-key topic)]
    (= identity (s/get storage key))))