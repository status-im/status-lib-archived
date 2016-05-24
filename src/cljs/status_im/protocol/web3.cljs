(ns status-im.protocol.web3
  (:require [cljs.core.async :refer [chan put! close! <!]]
            [cljsjs.web3]
            [status-im.utils.logging :as log]
            [status-im.utils.random :as random]
            [status-im.utils.encryption :refer [encrypt]]
            [status-im.protocol.state.state :as state]
            [status-im.protocol.user-handler :refer [invoke-user-handler]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def status-app-topic "STATUS-APP-CHAT-TOPIC")
(def status-msg-ttl 100)

(defn to-hex [s]
  (.toHex js/Web3.prototype s))

(defn to-utf8 [s]
  (.toUtf8 js/Web3.prototype s))

(defn whisper [web3]
  (.-shh web3))

(defn make-topics [topics]
  (->> {:topics (mapv to-hex topics)}
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

(defn create-identity [web3]
  (let [result-channel (chan)]
    (.sendAsync (.-currentProvider web3)
                (clj->js [{:jsonrpc "2.0" :method "shh_createIdentity" :params [] :id 99999999999}])
                (fn [error result]
                  (if error
                    (do
                      (log/error (str "Call to shh_createIdentity failed" ":") error)
                      (invoke-user-handler :error {:error-msg "Call to shh_createIdentity failed"
                                                   :details   error}))
                    (let [[public private] (-> (js->clj result :keywordize-keys true)
                                               (first)
                                               :result)]
                      (put! result-channel {:public  public
                                            :private private})))
                  (close! result-channel)))
    result-channel))

(defn add-identity [web3 private-key]
  (let [result-channel (chan)]
    (.sendAsync (.-currentProvider web3)
                (clj->js [{:jsonrpc "2.0" :method "shh_addIdentity" :params [private-key] :id 99999999999}])
                (fn [error result]
                  (if error
                    (do
                      (log/error (str "Call to shh_addIdentity failed" ":") error)
                      (invoke-user-handler :error {:error-msg "Call to shh_addIdentity failed"
                                                   :details   error}))
                    (put! result-channel (js->clj result)))
                  (close! result-channel)))
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
    :or   {ttl    status-msg-ttl
           topics []}}]
  (let [msg-id (random/id)]
    {:msg-id msg-id
     :msg    (cond-> {:ttl     ttl
                      :topics  (->> (conj topics status-app-topic)
                                    (mapv to-hex))
                      :payload (cond->> (merge payload {:msg-id msg-id})
                                        true (clj->js)
                                        encrypt? (encrypt-payload public-key clear-info)
                                        true (to-hex))}
                     from (assoc :from from)
                     to (assoc :to to))}))



(defn listen
  "Returns a filter which can be stopped with (stop-whisper-listener)"
  ([web3 msg-handler]
   (listen web3 msg-handler {}))
  ([web3 msg-handler {:keys [topics] :as opts :or {topics []}}]
   (let [topics (conj topics status-app-topic)
         shh    (whisper web3)
         filter (.filter shh (make-topics topics) (fn [error msg]
                                                    (if error
                                                      (invoke-user-handler :error {:error-msg error})
                                                      (msg-handler web3 msg))))]
     (state/add-filter topics filter))))

(defn stop-listener [group-topic]
  (let [topics (conj [group-topic] status-app-topic)
        filter (state/get-filter topics)]
    (when filter
      (do
        (.stopWatching filter)
        (state/remove-filter topics)))))
