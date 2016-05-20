(ns status-im.protocol.state.storage
  (:refer-clojure :exclude [get]))

(defprotocol Storage
  (put [this key value])
  (get [this key])
  (contains-key? [this key])
  (delete [this key]))
