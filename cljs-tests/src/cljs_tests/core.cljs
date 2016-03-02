(ns cljs-tests.core
  (:require [cljs-tests.whisper-protocol :as p]))

(enable-console-print!)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )




(comment
  ;;;;;;;;;;;; CHAT USER 1
  (def web3 (p/make-web3 "http://localhost:8545"))
  (def user1-ident (p/new-identity web3))
  (def listener (p/whisper-listen web3 (fn [err]
                                         (println "Whisper listener caught an error: " (js->clj err)))))


  (def web3-2 (p/make-web3 "http://localhost:8546"))
  (def user2-ident (p/new-identity web3-2))
  (p/send-message web3-2 user2-ident user1-ident "Hello World!")


  )

(comment
  ;;;;;;;;;;;; CHAT USER 2

  (use 'figwheel-sidecar.repl-api)
  (cljs-repl)
  )