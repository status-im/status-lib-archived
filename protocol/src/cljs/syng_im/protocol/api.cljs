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
                                                       save-identities]]
            [syng-im.protocol.delivery :refer [start-delivery-loop]]
            [syng-im.protocol.web3 :refer [listen
                                           make-msg
                                           post-msg
                                           make-web3
                                           new-identity]]
            [syng-im.protocol.handler :refer [handle-incoming-whisper-msg]]
            [syng-im.protocol.user-handler :refer [invoke-user-handler]]
            [syng-im.utils.encryption :refer [new-keypair]]
            [syng-im.protocol.group-chat :refer [send-group-msg]]
            [syng-im.protocol.defaults :refer [default-content-type]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn create-connection [ethereum-rpc-url]
  (make-web3 ethereum-rpc-url))

(defn create-identity [connection]
  (new-identity connection))

(defn my-identity []
  (state/my-identity))

(defn init-protocol
  "Required [handler ethereum-rpc-url storage]
   Optional [whisper-identity - if not passed a new identity is created automatically
             active-group-ids - list of active group ids]

   (fn handler [{:keys [event-type...}])

   :event-type can be:

   :new-msg - [from payload]
   :new-group-msg [from payload]
   :error - [error-msg details]
   :msg-acked [msg-id from]
   :delivery-failed [msg-id]
   :new-group-chat [from group-id]
   :group-chat-invite-acked [from group-id]
   :group-new-participant [identity group-id]
   :initialized [identity]

   :new-msg, new-group-msg, msg-acked should be handled idempotently (may be called multiple times for the same msg-id)
   "
  [{:keys [handler ethereum-rpc-url storage identity active-group-ids]}]
  (set-storage storage)
  (set-handler handler)
  (go
    (let [connection (create-connection ethereum-rpc-url)
          identity   (or identity
                         (<! (create-identity connection)))]
      (set-connection connection)
      (set-identity identity)
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

(defn start-group-chat [identities]
  (let [group-topic (random/id)
        keypair     (new-keypair)
        store       (storage)
        connection  (connection)
        my-identity (state/my-identity)
        identities  (-> (set identities)
                        (conj my-identity))]
    (save-keypair store group-topic keypair)
    (save-identities store group-topic identities)
    (listen connection handle-incoming-whisper-msg {:topics [group-topic]})
    (doseq [ident identities :when (not (= ident my-identity))]
      (let [{:keys [msg-id msg]} (make-msg {:from    my-identity
                                            :to      ident
                                            :payload {:type        :init-group-chat
                                                      :group-topic group-topic
                                                      :identities  identities
                                                      :keypair     keypair}})]
        (add-pending-message msg-id msg {:internal? true})
        (post-msg connection msg)))
    group-topic))

(defn group-add-participant [group-id new-peer-identity]
  (let [store      (storage)
        connection (connection)
        identities (-> (get-identities store group-id)
                       (conj new-peer-identity))
        keypair    (get-keypair store group-id)]
    (save-identities store group-id identities)
    (let [{:keys [msg-id msg]} (make-msg {:from    (state/my-identity)
                                          :to      new-peer-identity
                                          :payload {:type        :init-group-chat
                                                    :group-topic group-id
                                                    :identities  identities
                                                    :keypair     keypair}})]
      (add-pending-message msg-id msg {:internal? true})
      (post-msg connection msg))
    (send-group-msg {:group-id  group-id
                     :type      :group-new-participant
                     :payload   {:identity new-peer-identity}
                     :internal? true})))

(defn current-connection []
  (connection))
