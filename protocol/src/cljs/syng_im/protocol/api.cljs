(ns syng-im.protocol.api
  (:require [cljs.core.async :refer [<! timeout]]
            [syng-im.utils.random :as random]
            [syng-im.protocol.state.state :as state :refer [set-storage
                                                            set-handler
                                                            set-connection
                                                            set-identity
                                                            connection
                                                            storage]]
            [syng-im.protocol.state.delivery :refer [add-pending-message]]
            [syng-im.protocol.state.group-chat :refer [save-keypair
                                                       get-keypair
                                                       get-peer-identities
                                                       get-identities
                                                       save-identities
                                                       save-group-admin
                                                       group-admin?
                                                       remove-group-data]]
            [syng-im.protocol.delivery :refer [start-delivery-loop]]
            [syng-im.protocol.web3 :refer [listen
                                           make-msg
                                           post-msg
                                           make-web3
                                           create-identity
                                           add-identity
                                           stop-listener]]
            [syng-im.protocol.handler :refer [handle-incoming-whisper-msg]]
            [syng-im.protocol.user-handler :refer [invoke-user-handler]]
            [syng-im.utils.encryption :refer [new-keypair]]
            [syng-im.protocol.group-chat :refer [send-group-msg
                                                 init-group-chat-msg
                                                 group-add-participant-msg
                                                 group-remove-participant-msg
                                                 removed-from-group-msg]]
            [syng-im.protocol.defaults :refer [default-content-type]]
            [syng-im.utils.logging :as log])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn create-connection [ethereum-rpc-url]
  (make-web3 ethereum-rpc-url))

(defn my-identity []
  (state/my-identity))

(defn init-protocol
  "Required [handler ethereum-rpc-url storage]
   Optional [whisper-identity - if not passed a new identity is created automatically
             active-group-ids - list of active group ids]

   (fn handler [{:keys [event-type...}])

   :event-type can be:

   :new-msg - [from to payload]
   :new-group-msg [from group-id payload]
   :error - [error-msg details]
   :msg-acked [msg-id from]
   :delivery-failed [msg-id]
   :new-group-chat [from group-id]
   :group-chat-invite-acked [from group-id]
   :group-new-participant [identity group-id]
   :group-removed-participant [from identity group-id]
   :removed-from-group [from group-id]
   :participant-left-group [from group-id]
   :initialized [identity]

   :new-msg, new-group-msg, msg-acked should be handled idempotently (may be called multiple times for the same msg-id)
   "
  [{:keys [handler ethereum-rpc-url storage identity active-group-ids]}]
  (set-storage storage)
  (set-handler handler)
  (go
    (let [connection (create-connection ethereum-rpc-url)
          {:keys [public private] :as identity} (if identity
                                                  (do
                                                    (<! (->> (:private identity)
                                                             (add-identity connection)))
                                                    identity)
                                                  (<! (create-identity connection)))]
      (set-connection connection)
      (set-identity public)
      (listen connection handle-incoming-whisper-msg)
      (start-delivery-loop)
      (doseq [group-id active-group-ids]
        (listen connection handle-incoming-whisper-msg {:topics [group-id]}))
      (invoke-user-handler :initialized {:identity identity}))))

(defn send-user-msg [{:keys [to content]}]
  (let [{:keys [msg-id msg] :as new-msg} (make-msg {:from    (state/my-identity)
                                                    :to      to
                                                    :payload {:content      content
                                                              :content-type default-content-type
                                                              :type         :user-msg}})]
    (add-pending-message msg-id msg)
    (post-msg (connection) msg)
    new-msg))

(defn send-group-user-msg [{:keys [group-id content]}]
  (send-group-msg {:group-id group-id
                   :type     :group-user-msg
                   :payload  {:content      content
                              :content-type default-content-type}}))

(defn start-group-chat
  [identities]
  (let [group-topic (random/id)
        keypair     (new-keypair)
        store       (storage)
        connection  (connection)
        my-identity (state/my-identity)
        identities  (-> (set identities)
                        (conj my-identity))]
    (save-keypair store group-topic keypair)
    (save-identities store group-topic identities)
    (save-group-admin store group-topic my-identity)
    (listen connection handle-incoming-whisper-msg {:topics [group-topic]})
    (doseq [ident identities :when (not (= ident my-identity))]
      (let [{:keys [msg-id msg]} (init-group-chat-msg ident group-topic identities keypair)]
        (add-pending-message msg-id msg {:internal? true})
        (post-msg connection msg)))
    group-topic))

(defn group-add-participant
  "Only call if you are the group-admin"
  [group-id new-peer-identity]
  (let [store       (storage)
        my-identity (my-identity)]
    (if-not (group-admin? store group-id my-identity)
      (log/error "Called group-add-participant but not group admin, group-id:" group-id "my-identity:" my-identity)
      (let [connection (connection)
            identities (-> (get-identities store group-id)
                           (conj new-peer-identity))
            keypair    (get-keypair store group-id)]
        (save-identities store group-id identities)
        (let [{:keys [msg-id msg]} (group-add-participant-msg new-peer-identity group-id identities keypair)]
          (add-pending-message msg-id msg {:internal? true})
          (post-msg connection msg))
        (send-group-msg {:group-id  group-id
                         :type      :group-new-participant
                         :payload   {:identity new-peer-identity}
                         :internal? true})))))

(defn group-remove-participant
  "Only call if you are the group-admin"
  [group-id identity-to-remove]
  (let [store       (storage)
        my-identity (my-identity)]
    (if-not (group-admin? store group-id my-identity)
      (log/error "Called group-remove-participant but not group admin, group-id:" group-id "my-identity:" my-identity)
      (let [connection (connection)
            identities (-> (get-identities store group-id)
                           (disj identity-to-remove))
            keypair    (new-keypair)]
        (save-identities store group-id identities)
        (save-keypair store group-id keypair)
        (doseq [ident identities :when (not (= ident my-identity))]
          (let [{:keys [msg-id msg]} (group-remove-participant-msg ident group-id keypair identity-to-remove)]
            (add-pending-message msg-id msg {:internal? true})
            (post-msg connection msg)))
        (let [{:keys [msg-id msg]} (removed-from-group-msg group-id identity-to-remove)]
          (add-pending-message msg-id msg {:internal? true})
          (post-msg connection msg))))))

(defn leave-group-chat [group-id]
  (let [store       (storage)
        my-identity (my-identity)]
    (send-group-msg {:group-id  group-id
                     :type      :left-group
                     :payload   {:identity my-identity}
                     :internal? true})
    (remove-group-data store group-id)
    (stop-listener group-id)))

(defn current-connection []
  (connection))
