(ns cljs-tests.protocol.state.delivery
  (:require [cljs-time.core :as t]
            [cljs-tests.protocol.state.state :refer [state]]
            [cljs-tests.utils.logging :as log]))

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

(defn add-pending-message [msg-id msg]
  (swap! state (fn [state]
                 (-> (assoc-in state [:pending-messages msg-id] {:msg         msg
                                                                 :retry-count 0})
                     (push-msg-to-delivery-queue msg-id)))))

(defn pop-delivery-queue []
  (swap! state update-in [:delivery-queue] pop))

(defn push-delivery-queue [msg-id]
  (swap! state push-msg-to-delivery-queue msg-id))

(defn remove-pending-message [msg-id]
  (log/info "Removing message" msg-id "from pending")
  (swap! state update-in [:pending-messages] dissoc msg-id))

(defn delivery-queue []
  (:delivery-queue @state))
