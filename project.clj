(defproject lustered "0.1.0-SNAPSHOT"
  :description "Admin site as data"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.6.1"
  
  :dependencies [;; for server side
                 [org.clojure/clojure "1.8.0"]
                 [mount "0.1.10"]
                 [org.clojure/core.async "0.2.374"
                  :exclusions [org.clojure/tools.reader]]
                 [ring-middleware-format "0.7.0"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-defaults "0.2.0"]
                 [compojure "1.5.0"]
                 [enlive "1.1.6"]

                 ;; for client side
                 [org.clojure/clojurescript "1.8.40"]
                 [cljs-ajax "0.5.4"]
                 [bidi "2.0.4"]
                 [reagent "0.5.1" :exclusions [cljsjs/react]]
                 [re-frame "0.7.0"]
                 [cljsjs/jquery "2.2.2-0"]
                 [cljsjs/react-bootstrap "0.28.1-1"
                  :exclusions [org.webjars.bower/jquery]]]

  :plugins [[lein-figwheel "0.5.2"]
            [lein-cljsbuild "1.1.3" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src/clj" "src/cljc"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]
  :auto-clean false

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/cljs" "src/cljc"]

                ;; If no code is to be run, set :figwheel true for continued automagical reloading
                :figwheel {:on-jsload "lustered.core/on-js-reload"}

                :compiler {:main lustered.main
                           :asset-path "../js/compiled/out"
                           :output-to "resources/public/js/compiled/lustered.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true}}
               ;; This next build is an compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id "min"
                :source-paths ["src/cljs" "src/cljc"]
                :compiler {:output-to "resources/public/js/compiled/lustered.js"
                           :main lustered.main
                           :optimizations :advanced
                           :pretty-print false}}]}

  :profiles {:dev {:source-paths ["env" "examples"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [environ "1.0.2"]
                                  [ring/ring-jetty-adapter "1.4.0"]
                                  [clj-time "0.11.0"]]}}

  :figwheel {;; :http-server-root "public" ;; default and assumes "resources"
             ;; :server-port 3449 ;; default
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"
             })
