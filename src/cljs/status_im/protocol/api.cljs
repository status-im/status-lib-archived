(ns status-im.protocol.api
  (:require [cljs.core.async :refer [<! timeout]]
            [status-im.utils.random :as random]
            [status-im.protocol.state.state :as state :refer [set-storage
                                                            set-handler
                                                            set-connection
                                                            set-identity
                                                            connection
                                                            storage]]
            [status-im.protocol.state.delivery :refer [add-pending-message]]
            [status-im.protocol.state.group-chat :refer [save-keypair
                                                       get-keypair
                                                       get-peer-identities
                                                       get-identities
                                                       save-identities
                                                       save-group-admin
                                                       save-group-name
                                                       group-admin?
                                                       remove-group-data
                                                       group-name]]
            [status-im.protocol.state.discovery :refer [save-topics
                                                      save-hashtags
                                                      get-topics
                                                      save-status
                                                      save-name]]
            [status-im.protocol.delivery :refer [start-delivery-loop]]
            [status-im.protocol.web3 :refer [listen
                                           make-msg
                                           post-msg
                                           make-web3
                                           create-identity
                                           add-identity
                                           stop-listener]]
            [status-im.protocol.handler :refer [handle-incoming-whisper-msg]]
            [status-im.protocol.user-handler :refer [invoke-user-handler]]
            [status-im.utils.encryption :refer [new-keypair]]
            [status-im.protocol.group-chat :refer [send-group-msg
                                                 init-group-chat-msg
                                                 group-add-participant-msg
                                                 group-remove-participant-msg
                                                 removed-from-group-msg]]
            [status-im.protocol.discovery :refer [init-discovery
                                                get-hashtag-topics
                                                discovery-response-topic
                                                discovery-search-topic
                                                discovery-search-message
                                                broadcast-status]]
            [status-im.protocol.defaults :refer [default-content-type]]
            [status-im.utils.logging :as log])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn create-connection [ethereum-rpc-url]
  (make-web3 ethereum-rpc-url))

(defn my-identity []
  (state/my-identity))

(defn init-protocol
  "Required [handler ethereum-rpc-url storage]
   Optional [identity - if not passed a new identity is created automatically
             active-group-ids - list of active group ids]

   (fn handler [{:keys [event-type...}])

   :event-type can be:

   :new-msg - [from to payload]
   :new-group-msg [from group-id payload]
   :error - [error-msg details]
   :msg-acked [msg-id from]
   :delivery-failed [msg-id]
   :new-group-chat [from group-id]
   :group-chat-invite-acked [ack-msg-id from group-id]
   :group-new-participant [identity group-id from msg-id]
   :group-removed-participant [from identity group-id msg-id]
   :removed-from-group [from group-id msg-id]
   :participant-left-group [from group-id msg-id]
   :initialized [identity]

   :new-msg, new-group-msg, msg-acked should be handled idempotently (may be called multiple times for the same msg-id)
   "
  ([db] (init-protocol "no-identity" db))
  ([public-key {:keys [handler ethereum-rpc-url storage identity active-group-ids]}]
  (set-storage storage)
  (set-handler handler)
  (go
    (let [connection (create-connection ethereum-rpc-url)]
      (set-connection connection)
      (set-identity public-key)
      (listen connection handle-incoming-whisper-msg)
      (start-delivery-loop)
      (doseq [group-id active-group-ids]
        (listen connection handle-incoming-whisper-msg {:topics [group-id]}))
      (init-discovery)
      (listen connection handle-incoming-whisper-msg {:topics [discovery-response-topic]})
      (invoke-user-handler :initialized {:identity {:public public-key}})))))

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
  ([identities]
   (start-group-chat identities nil))
  ([identities group-name]
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
     (save-group-name store group-topic group-name)
     (listen connection handle-incoming-whisper-msg {:topics [group-topic]})
     (doseq [ident identities :when (not (= ident my-identity))]
       (let [{:keys [msg-id msg]} (init-group-chat-msg ident group-topic identities keypair group-name)]
         (add-pending-message msg-id msg {:internal? true})
         (post-msg connection msg)))
     group-topic)))

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
            keypair    (get-keypair store group-id)
            group-name (group-name store group-id)]
        (save-identities store group-id identities)
        (let [{:keys [msg-id msg]} (group-add-participant-msg new-peer-identity group-id group-name identities keypair)]
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

(defn stop-broadcasting-discover []
  (let [topics (get-topics)]
    (doseq [topic topics]
      (stop-listener topic))
    (save-hashtags [])))

(defn broadcast-discover-status [name status hashtags]
  (log/debug "Broadcasting status: " name status hashtags)
  (let [topics (get-hashtag-topics hashtags)]
      (stop-broadcasting-discover)
      (listen (connection) handle-incoming-whisper-msg {:topics topics})
      (save-name name)
      (save-topics topics)
      (save-hashtags hashtags)
      (save-status status)
      (broadcast-status)))

(defn search-discover [hashtags]
  (let [{:keys [msg-id msg]} (discovery-search-message hashtags)]
    (add-pending-message msg-id msg {:internal? true})
    (post-msg connection msg)))

(defn current-connection []
  (connection))
