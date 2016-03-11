(ns syng-im.core
  (:require [clojure.string :as s]
            [syng-im.protocol.api :as p]
            [syng-im.utils.logging :as log]
            [goog.dom :as g]
            [goog.dom.forms :as f]
            [goog.events :as e]
            [goog.events.EventType]
            [goog.events.KeyCodes]
            [goog.events.KeyHandler]
            [goog.events.KeyHandler.EventType :as key-handler-events]
            [syng-im.protocol.state.storage :as st])
  (:import [goog.events EventType]
           [goog.events KeyCodes]))

(enable-console-print!)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )

(defrecord MapStore [m]
  st/Storage
  (put [this key value]
    (swap! m assoc key value))
  (get [this key]
    (get @m key))
  (contains-key? [this key]
    (contains? @m key))
  (delete [this key]
    (swap! m dissoc key)))

(defonce state (atom {:group-id         nil
                      :group-identities nil
                      :storage          (map->MapStore {:m (atom {})})}))

(defn shorten [s]
  (subs s 0 6))

(defn set-group-id! [group-id]
  (swap! state assoc :group-id group-id))

(defn add-to-chat [element-id from content]
  (let [chat-area (g/getElement element-id)
        chat      (f/getValue chat-area)
        chat      (str chat (shorten from) ": " content "\n")]
    (f/setValue chat-area chat)))

(defn set-group-identities [identities]
  (let [ids (s/join "\n" identities)]
    (-> (g/getElement "to-identities")
        (f/setValue ids))))

(defn get-group-identities []
  (-> (g/getElement "to-identities")
      (f/getValue)
      (s/split "\n")))

(defn add-identity-to-group-list [identity]
  (-> (get-group-identities)
      (set)
      (conj identity)
      (set-group-identities)))

(defn remove-identity-from-group-list [identity]
  (-> (get-group-identities)
      (set)
      (disj identity)
      (set-group-identities)))

(defn start []
  (let [rpc-url (-> (g/getElement "rpc-url")
                    (f/getValue))]
    (p/init-protocol
      {:ethereum-rpc-url rpc-url
       :storage          (:storage @state)
       :handler          (fn [{:keys [event-type] :as event}]
                           (log/info "Event:" (clj->js event))
                           (case event-type
                             :new-msg (let [{from               :from
                                             {content :content} :payload} event]
                                        (add-to-chat "chat" from content))
                             :msg-acked (let [{:keys [msg-id]} event]
                                          (add-to-chat "chat" ":" (str "Message " msg-id " was acked")))
                             :initialized (let [{:keys [identity]} event]
                                            (add-to-chat "chat" ":" (str "Initialized, identity is " identity))
                                            (-> (g/getElement "my-identity")
                                                (f/setValue identity)))
                             :delivery-failed (let [{:keys [msg-id]} event]
                                                (add-to-chat "chat" ":" (str "Delivery of message " msg-id " failed")))
                             :new-group-chat (let [{:keys [from group-id identities]} event]
                                               (set-group-id! group-id)
                                               (set-group-identities identities)
                                               (add-to-chat "group-chat" ":" (str "Received group chat invitation from " from " for group-id: " group-id)))
                             :group-chat-invite-acked (let [{:keys [from group-id]} event]
                                                        (add-to-chat "group-chat" ":" (str "Received ACK for group chat invitation from " from " for group-id: " group-id)))
                             :new-group-msg (let [{from               :from
                                                   {content :content} :payload} event]
                                              (add-to-chat "group-chat" from content))
                             :group-new-participant (let [{:keys [group-id identity from]} event]
                                                      (add-to-chat "group-chat" ":" (str (shorten from) " added " (shorten identity) " to group chat"))
                                                      (add-identity-to-group-list identity))
                             :group-removed-participant (let [{:keys [group-id identity from]} event]
                                                          (add-to-chat "group-chat" ":" (str (shorten from) " removed " (shorten identity) " from group chat"))
                                                          (remove-identity-from-group-list identity))
                             :removed-from-group (let [{:keys [group-id from]} event]
                                                   (add-to-chat "group-chat" ":" (str (shorten from) " removed you from group chat")))
                             :participant-left-group (let [{:keys [group-id from]} event]
                                                       (add-to-chat "group-chat" ":" (str (shorten from) " left group chat")))
                             (add-to-chat "chat" ":" (str "Don't know how to handle " event-type))))})
    (e/listen (-> (g/getElement "msg")
                  (goog.events.KeyHandler.))
      key-handler-events/KEY
      (fn [e]
        (when (= (.-keyCode e) KeyCodes/ENTER)
          (let [msg         (-> (g/getElement "msg")
                                (f/getValue))
                to-identity (-> (g/getElement "to-identity")
                                (f/getValue))]
            (p/send-user-msg {:to      to-identity
                              :content msg})
            (add-to-chat "chat" (p/my-identity) msg)))))
    (e/listen (-> (g/getElement "group-msg")
                  (goog.events.KeyHandler.))
      key-handler-events/KEY
      (fn [e]
        (when (= (.-keyCode e) KeyCodes/ENTER)
          (let [msg      (-> (g/getElement "group-msg")
                             (f/getValue))
                group-id (:group-id @state)]
            (p/send-group-user-msg {:group-id group-id
                                    :content  msg})
            (add-to-chat "group-chat" (p/my-identity) msg)))))))

(defn start-group-chat []
  (let [identities (get-group-identities)]
    (add-to-chat "group-chat" ":" (str "Starting group chat with " identities))
    (let [group-id (p/start-group-chat identities)]
      (set-group-id! group-id))))

(defn add-new-peer-to-group []
  (let [input        (g/getElement "new-peer")
        new-identity (-> input (f/getValue))
        group-id     (:group-id @state)]
    (f/setValue input "")
    (p/group-add-participant group-id new-identity)))

(defn remove-peer-from-group []
  (let [input            (g/getElement "new-peer")
        removed-identity (-> input (f/getValue))
        group-id         (:group-id @state)]
    (f/setValue input "")
    (remove-identity-from-group-list removed-identity)
    (p/group-remove-participant group-id removed-identity)))

(defn leave-group []
  (let [group-id (:group-id @state)]
    (p/leave-group-chat group-id)))

(let [button (g/getElement "connect-button")]
  (e/listen button EventType/CLICK
    (fn [e]
      (g/setProperties button #js {:disabled "disabled"})
      (start))))

(let [button (g/getElement "start-group-chat-button")]
  (e/listen button EventType/CLICK
    (fn [e]
      (g/setProperties button #js {:disabled "disabled"})
      (g/setProperties (g/getElement "to-identities") #js {:disabled "disabled"})
      (start-group-chat))))

(let [button (g/getElement "add-peer-button")]
  (e/listen button EventType/CLICK
    (fn [e]
      (add-new-peer-to-group))))

(let [button (g/getElement "remove-peer-button")]
  (e/listen button EventType/CLICK
    (fn [e]
      (remove-peer-from-group))))

(let [button (g/getElement "leave-group-button")]
  (e/listen button EventType/CLICK
    (fn [e]
      (leave-group))))

(comment

  (p/init-protocol {:ethereum-rpc-url "http://localhost:4546"
                    :handler          (fn [{:keys [event-type] :as event}]
                                        (log/info "Event:" (clj->js event)))})

  (p/send-user-msg {:to      "0x04a877ae4dcd6005c8f4a576f8c11df56889f5252360cbf7e274bfcfc13f4028f10a3e29ebbb4af12c751d989fbaba09c570a78bc2c5e55773f0ee8579355a1358"
                    :content "Hello World!"})

  (p/my-identity)

  )


(comment



  ;;;;;;;;;;;; CHAT USER 1
  ;(def web3 (p/make-web3 "http://localhost:4546"))
  ;(def user1-ident (p/new-identity web3))
  ;(def listener (p/whisper-listen web3 (fn [err]
  ;                                       (println "Whisper listener caught an error: " (js->clj err)))))
  ;
  ;
  ;(def web3-2 (p/make-web3 "http://localhost:4547"))
  ;(def user2-ident (p/new-identity web3-2))
  ;(p/make-whisper-msg web3-2 user2-ident user1-ident "Hello World!")


  (require '[syng-im.protocol.whisper :as w])
  (def web3 (w/make-web3 "http://localhost:4546"))
  (.newIdentity (w/whisper web3) (fn [error result]
                                   (println error result)))

  )

(comment
  ;;;;;;;;;;;; CHAT USER 2

  (use 'figwheel-sidecar.repl-api)
  (cljs-repl)
  )