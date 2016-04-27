(ns syng-im.protocol.state.delivery
  (:require [cljs-time.core :as t]
            [syng-im.utils.logging :as log]
            [syng-im.protocol.state.state :refer [state]])
  (:require-macros [syng-im.utils.lang-macros :refer [condas->]]))

(defn inc-retry-count [msg-id]
  (swap! state (fn [state]
                 (if (get-in state [:pending-messages msg-id])
                   (update-in state [:pending-messages msg-id :retry-count] inc)
                   state))))

(defn pending? [msg-id]
  (get-in @state [:pending-messages msg-id]))

(defn push-msg-to-delivery-queue [state msg-id]
  (update-in state [:delivery-queue] conj {:timestamp (t/now)
                                           :msg-id    msg-id}))

(defn add-pending-message
  ([msg-id msg {:keys [identities internal?] :as opts}]
   (log/debug "add-pending-message" msg-id msg identities internal?)
   (swap! state (fn [state]
                  (-> (assoc-in state [:pending-messages msg-id] {:msg         msg
                                                                  :retry-count 0
                                                                  :identities  identities
                                                                  :internal?   internal?})
                      (push-msg-to-delivery-queue msg-id)))))
  ([msg-id msg]
   (add-pending-message msg-id msg nil)))

(defn pop-delivery-queue []
  (swap! state update-in [:delivery-queue] pop))

(defn push-delivery-queue [msg-id]
  (swap! state push-msg-to-delivery-queue msg-id))

(defn internal? [msg-id]
  (get-in @state [:pending-messages msg-id :internal?]))

(defn update-pending-message [msg-id from]
  (swap! state update-in [:pending-messages]
         (fn [pending-msgs]
           (condas-> pending-msgs msgs
                     (get-in msgs [msg-id :identities])     ;; test
                     (do
                       (log/info "Removing identity" from "from pending msg" msg-id)
                       (update-in msgs [msg-id :identities] disj from))

                     (empty? (get-in msgs [msg-id :identities])) ;; test
                     (do
                       (log/info "Removing message" msg-id "from pending")
                       (dissoc msgs msg-id))))))

(defn remove-pending-message [msg-id]
  (swap! state update-in [:pending-messages] dissoc msg-id))

(defn delivery-queue []
  (:delivery-queue @state))
