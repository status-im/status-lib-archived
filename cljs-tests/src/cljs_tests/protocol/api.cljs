(ns cljs-tests.protocol.api
  (:require [cljs-tests.protocol.state.state :as state]
            [cljs-tests.protocol.delivery :as delivery]
            [cljs-tests.protocol.state.delivery :as delivery-state]
            [cljs-tests.protocol.whisper :as whisper]))

(def default-content-type "text/plain")

(defn create-connection [ethereum-rpc-url]
  (whisper/make-web3 ethereum-rpc-url))

(defn create-identity [connection]
  (whisper/new-identity connection))

(defn init-protocol
  "Required [handler ethereum-rpc-url]
   Optional [whisper-identity] - if not passed a new identity is created automatically

   (fn handler [{:keys [event-type...}])

   :event-type can be:

   :new-msg - [from payload]
   :error - [error-msg]
   :msg-acked [msg-id]
   :delivery-failed [msg-id]

   :new-msg, msg-acked should be handled idempotently (may be called multiple times for the same msg-id)
   "
  [{:keys [handler ethereum-rpc-url identity]}]
  (state/set-handler handler)
  (let [connection (create-connection ethereum-rpc-url)
        identity   (or identity
                       (create-identity connection))]
    (state/set-connection connection)
    (state/set-identity identity)
    (whisper/listen connection)
    (delivery/start-delivery-loop)
    {:identity identity}))

(defn send-user-msg [{:keys [to content]}]
  (let [{:keys [msg-id msg] :as new-msg} (whisper/make-msg {:from    (state/my-identity)
                                                            :to      to
                                                            :payload {:content      content
                                                                      :content-type default-content-type
                                                                      :type         :user-msg}})]
    (delivery-state/add-pending-message msg-id msg)
    (whisper/post-msg (state/connection) msg)
    new-msg))

(defn my-identity []
  (state/my-identity))

(defn current-connection []
  (state/connection))