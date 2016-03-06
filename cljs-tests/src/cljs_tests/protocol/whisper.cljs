(ns cljs-tests.protocol.whisper
  (:require [cljs.core.async :refer [chan put! close! <!]]
            [cljsjs.web3]
            [cljsjs.chance]
            [cljs-tests.utils.logging :as log]
            [cljs-tests.protocol.state.state :as state]
            [cljs-tests.protocol.state.delivery :as delivery]
            [cljs-tests.protocol.handler :as handler]
            [cljs.reader :refer [read-string]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

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

(defn make-callback [{:keys [error-msg result-channel]}]
  (fn [error result]
    (if error
      (do
        (log/error (str error-msg ":") error)
        (handler/invoke-handler :error {:error-msg error-msg
                                        :details   error}))
      (put! result-channel result))
    (close! result-channel)))

(defn new-identity [web3]
  (let [result-channel (chan)
        callback       (make-callback {:error-msg      "Call to newIdentity failed"
                                       :result-channel result-channel})]
    (.newIdentity (.-shh web3) callback)
    result-channel))

(defn handle-ack [{:keys [ack-msg-id]}]
  (log/info "Got ack for message:" ack-msg-id)
  (delivery/remove-pending-message ack-msg-id)
  (handler/invoke-handler :msg-acked {:msg-id ack-msg-id}))

(defn post-msg [web3 msg]
  (let [js-msg (clj->js msg)]
    (log/info "Sending whisper message:" js-msg)
    (-> (whisper web3)
        (.post js-msg (fn [error result]
                        (when error
                          (let [error-msg "Call to shh.post() failed"]
                            (log/error (str error-msg ":") error)
                            (handler/invoke-handler :error {:error-msg error-msg
                                                            :details   error}))))))))

(defn make-msg
  "Returns [msg-id msg], `msg` is formed for Web3.shh.post()"
  [{:keys [from to ttl topics payload]
    :or   {ttl    syng-msg-ttl
           topics []}}]
  (let [msg-id (.guid js/chance)]
    {:msg-id msg-id
     :msg    (cond-> {:ttl     ttl
                      :topics  (->> (conj topics syng-app-topic)
                                    (mapv from-ascii))
                      :payload (->> (merge payload {:msg-id msg-id})
                                    (str)
                                    (from-ascii))}
                     from (assoc :from from)
                     to (assoc :to to))}))

(defn send-ack [web3 to msg-id]
  (log/info "Acking message:" msg-id "To:" to)
  (let [{:keys [msg]} (make-msg {:from    (state/my-identity)
                                 :to      to
                                 :payload {:type       :ack
                                           :ack-msg-id msg-id}})]
    (post-msg web3 msg)))

(defn handle-user-msg [web3 from {:keys [msg-id] :as payload}]
  (send-ack web3 from msg-id)
  (handler/invoke-handler :new-msg {:from    from
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
        filter (.filter shh (make-topics topics) (fn [error msg]
                                                   (if error
                                                     (handler/invoke-handler :error {:error-msg error})
                                                     (handle-arriving-whisper-msg web3 msg))))]
    (state/add-filter topics filter)))

(defn stop-listener [filter]
  (.stopWatching filter))




