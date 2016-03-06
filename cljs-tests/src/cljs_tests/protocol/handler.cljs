(ns cljs-tests.protocol.handler
  (:require [cljs-tests.protocol.state.state :as state]))

(defn invoke-handler [event-type params]
  ((state/handler) (assoc params :event-type event-type)))
