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
                                                       get-identities
                                                       save-identities]]
            [syng-im.protocol.delivery :refer [start-delivery-loop]]
            [syng-im.protocol.web3 :refer [listen make-msg
                                           post-msg
                                           make-web3
                                           new-identity]]
            [syng-im.protocol.handler :refer [handle-incoming-whisper-msg]]
            [syng-im.protocol.user-handler :refer [invoke-user-handler]]
            [syng-im.utils.encryption :refer [new-keypair]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def default-content-type "text/plain")

(defn create-connection [ethereum-rpc-url]
  (make-web3 ethereum-rpc-url))

(defn create-identity [connection]
  (new-identity connection))

(defn my-identity []
  (state/my-identity))

(defn init-protocol
  "Required [handler ethereum-rpc-url storage]
   Optional [whisper-identity] - if not passed a new identity is created automatically

   (fn handler [{:keys [event-type...}])

   :event-type can be:

   :new-msg - [from payload]
   :error - [error-msg details]
   :msg-acked [msg-id from]
   :delivery-failed [msg-id]
   :new-group-chat [from group-id]
   :group-chat-invite-acked [from group-id]
   :initialized [identity]

   :new-msg, msg-acked should be handled idempotently (may be called multiple times for the same msg-id)
   "
  [{:keys [handler ethereum-rpc-url storage identity]}]
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

(defn send-group-msg [{:keys [group-id content]}]
  (let [store (storage)
        {public-key :public} (get-keypair store group-id)
        {:keys [msg-id msg] :as new-msg} (make-msg {:from       (state/my-identity)
                                                    :topics     [group-id]
                                                    :encrypt    true
                                                    :public-key public-key
                                                    :payload    {:content      content
                                                                 :content-type default-content-type
                                                                 :type         :user-msg}})]
    (add-pending-message msg-id msg {:identities (get-identities store group-id)})
    (post-msg (connection) msg)
    new-msg))

(defn start-group-chat [identities]
  (let [group-topic (random/id)
        keypair     (new-keypair)]
    (let [store (storage)]
      (save-keypair store group-topic keypair)
      (save-identities store group-topic identities))
    (doseq [ident identities]
      (let [{:keys [msg-id msg]} (make-msg {:from    (state/my-identity)
                                            :to      ident
                                            :payload {:type        :init-group-chat
                                                      :group-topic group-topic
                                                      :identities  identities
                                                      :keypair     keypair}})]
        (add-pending-message msg-id msg {:internal? true})
        (post-msg (connection) msg)))
    group-topic))

(defn current-connection []
  (connection))




















