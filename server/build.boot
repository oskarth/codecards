(set-env! :resource-paths #{"src"}
          :dependencies '[[org.clojure/clojure "1.9.0-alpha14"]
                          [org.clojure/test.check "0.9.0" :scope "test"]
                          [org.clojure/core.async "0.2.395"]
                          [http-kit "2.2.0"]
                          [bidi "1.22.1"]
                          [clj-time "0.13.0"]
                          [ring/ring-json "0.4.0"]
                          [clj-mailgun "0.2.0"]
                          [jarohen/chime "0.2.0"]])

(deftask build []
  (comp
   (aot :namespace #{'fingertips.server})
   (uber)
   (jar :file "fingertips-api.jar" :main 'fingertips.server)
   (target)))

(require '[fingertips.server :as server])
(println "Starting REPL and server")
(server/run)
