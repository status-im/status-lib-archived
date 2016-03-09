(defproject syng-im/protocol "0.1.0"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.5.3"

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]
                 [org.clojure/core.async "0.2.374" :exclusions [org.clojure/tools.reader]]
                 [cljsjs/chance "0.7.3-0"]
                 [com.andrewmcveigh/cljs-time "0.4.0"]
                 [cljsjs/web3 "0.15.3-0"]]

  :plugins [[lein-cljsbuild "1.1.2" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {:builds
              [{:id           "dev"
                :source-paths ["src/cljs"]
                :compiler     {:asset-path           "js/compiled/out"
                               :output-to            "resources/public/js/compiled/protocol.js"
                               :output-dir           "resources/public/js/compiled/out"
                               :source-map-timestamp true}}
               ;; This next build is an compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id           "min"
                :source-paths ["src/cljs"]
                :compiler     {:output-to     "resources/public/js/compiled/protocol.js"
                               :optimizations :advanced
                               :pretty-print  false}}]}
  )
