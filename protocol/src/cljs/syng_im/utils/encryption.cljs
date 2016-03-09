(ns syng-im.utils.encryption
  (:require [cljsjs.chance]))

(defn new-keypair
  "Returns {:private \"private key\" :public \"public key\""
  []
  (let [random-fake (.guid js/chance)]
    {:private random-fake
     :public  random-fake}))

(defn encrypt [public-key content]
  content)

(defn decrypt [private-key content]
  content)