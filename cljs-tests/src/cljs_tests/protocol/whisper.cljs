(ns cljs-tests.protocol.whisper
  (:require [cljsjs.web3]
            [cljsjs.chance]
            [cljs-tests.utils.logging :as log]
            [cljs-tests.protocol.state :as state]
            [cljs.reader :refer [read-string]]))

(def syng-app-topic "SYNG-APP-CHAT-TOPIC")
(def syng-msg-ttl 100)

(defn from-ascii [s]
  (.fromAscii js/Web3.prototype s))

(defn to-ascii [s]
  (.toAscii js/Web3.prototype s))

(defn whisper [web3]
  (.-shh web3))

(defn make-topics [topics]
  (->> {:topics (mapv from-ascii topics)}
       (clj->js)))

(defn make-web3 [rpc-url]
  (->> (js/Web3.providers.HttpProvider. rpc-url)
       (js/Web3.)))

(defn new-identity [web3]
  (.newIdentity (.-shh web3)))

(defn invoke-handler [event-type params]
  ((state/handler) (assoc params :event-type event-type)))

(defn handle-ack [{:keys [ack-msg-id]}]
  (log/info "Got ack for message:" ack-msg-id)
  (state/remove-pending-message ack-msg-id)
  (invoke-handler :msg-acked {:msg-id ack-msg-id}))

(defn post-msg [web3 msg]
  (let [js-msg (clj->js msg)]
    (log/info "Sending whisper message:" js-msg)
    (-> (whisper web3)
        (.post js-msg))))

(defn make-msg
  "Returns [msg-id msg], `msg` is formed for Web3.shh.post()"
  [{:keys [from to ttl topics payload]
    :or   {ttl    syng-msg-ttl
           topics []}}]
  (let [msg-id (.guid js/chance)]
    [msg-id (cond-> {:ttl     ttl
                     :topics  (->> (conj topics syng-app-topic)
                                   (mapv from-ascii))
                     :payload (->> (merge payload {:msg-id msg-id})
                                   (str)
                                   (from-ascii))}
                    from (assoc :from from)
                    to (assoc :to to))]))

(defn send-ack [web3 to msg-id]
  (log/info "Acking message:" msg-id "To:" to)
  (let [[_ msg] (make-msg {:from    (state/my-identity)
                           :to      to
                           :payload {:type       :ack
                                     :ack-msg-id msg-id}})]
    (post-msg web3 msg)))

(defn handle-user-msg [web3 from {:keys [msg-id] :as payload}]
  (send-ack web3 from msg-id)
  (invoke-handler :new-msg {:from    from
                            :payload payload}))

(defn handle-arriving-whisper-msg [web3 msg]
  (log/info "Got whisper message:" msg)
  (let [{from    :from
         to      :to
         topics  :topics                                    ;; always empty (bug in go-ethereum?)
         payload :payload
         :as     msg} (js->clj msg :keywordize-keys true)]
    (if (= to (state/my-identity))
      (let [{msg-type :type
             msg-id   :msg-id
             :as      payload} (->> (to-ascii payload)
                                    (read-string))]
        (case msg-type
          :ack (handle-ack payload)
          :user-msg (handle-user-msg web3 from payload)))
      (log/warn "My identity:" (state/my-identity) "Message To:" to "Message is encrypted for someone else, ignoring"))))

(defn listen
  "Returns a filter which can be stopped with (stop-whisper-listener)"
  [web3]
  (let [topics [syng-app-topic]
        shh    (whisper web3)
        filter (.filter shh (make-topics topics))
        watch  (.watch filter (fn [error msg]
                                (if error
                                  (invoke-handler :error {:error-msg error})
                                  (handle-arriving-whisper-msg web3 msg))))]
    (state/add-filter topics filter)))

(defn stop-listener [filter]
  (.stopWatching filter))




