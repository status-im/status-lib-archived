(ns syng-im.protocol.web3
  (:require [cljs.core.async :refer [chan put! close! <!]]
            [cljsjs.web3]
            [syng-im.utils.logging :as log]
            [syng-im.utils.random :as random]
            [syng-im.utils.encryption :refer [encrypt]]
            [syng-im.protocol.state.state :as state]
            [syng-im.protocol.user-handler :refer [invoke-user-handler]])
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
        (invoke-user-handler :error {:error-msg error-msg
                                     :details   error}))
      (put! result-channel result))
    (close! result-channel)))

(defn new-identity [web3]
  (let [result-channel (chan)
        callback       (make-callback {:error-msg      "Call to newIdentity failed"
                                       :result-channel result-channel})]
    (.newIdentity (.-shh web3) callback)
    result-channel))

(defn post-msg [web3 msg]
  (let [js-msg (clj->js msg)]
    (log/info "Sending whisper message:" js-msg)
    (-> (whisper web3)
        (.post js-msg (fn [error result]
                        (when error
                          (let [error-msg "Call to shh.post() failed"]
                            (log/error (str error-msg ":") error)
                            (invoke-user-handler :error {:error-msg error-msg
                                                         :details   error}))))))))

(defn encrypt-payload [public-key clear-info payload-str]
  (->> (merge {:enc-payload (encrypt public-key payload-str)}
              clear-info)
       (str)))

(defn make-msg
  "Returns [msg-id msg], `msg` is formed for Web3.shh.post()"
  [{:keys [from to ttl topics payload encrypt? public-key clear-info]
    :or   {ttl    syng-msg-ttl
           topics []}}]
  (let [msg-id (random/id)]
    {:msg-id msg-id
     :msg    (cond-> {:ttl     ttl
                      :topics  (->> (conj topics syng-app-topic)
                                    (mapv from-ascii))
                      :payload (cond->> (merge payload {:msg-id msg-id})
                                        true (str)
                                        encrypt? (encrypt-payload public-key clear-info)
                                        true (from-ascii))}
                     from (assoc :from from)
                     to (assoc :to to))}))



(defn listen
  "Returns a filter which can be stopped with (stop-whisper-listener)"
  ([web3 msg-handler]
    (listen web3 msg-handler {}))
  ([web3 msg-handler {:keys [topics] :as opts :or {topics []}}]
   (let [topics (conj topics syng-app-topic)
         shh    (whisper web3)
         filter (.filter shh (make-topics topics) (fn [error msg]
                                                    (if error
                                                      (invoke-user-handler :error {:error-msg error})
                                                      (msg-handler web3 msg))))]
     (state/add-filter topics filter))))

(defn stop-listener [filter]
  (.stopWatching filter))




