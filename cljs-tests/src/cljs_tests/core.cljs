(ns cljs-tests.core
  (:require [cljs-tests.protocol.api :as p]
            [cljs-tests.utils.logging :as log]))

(enable-console-print!)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )


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

  )

(comment
  ;;;;;;;;;;;; CHAT USER 2

  (use 'figwheel-sidecar.repl-api)
  (cljs-repl)
  )