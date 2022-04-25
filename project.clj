(defproject com.oscaro/tools-io "0.3.25-SNAPSHOT"
  :description "Oscaro's generic I/O tools collection"
  :url "https://github.com/oscaro/tools-io"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.11.1"]

                 [clj-commons/clj-yaml "0.7.108"]
                 [com.cnuernber/charred "1.004"]]
  ;; https://www.blog.nodrama.io/travis-continuous-delivery/
  ;; https://github.com/technomancy/leiningen/issues/2307#issuecomment-610538144
  :deploy-repositories [["snapshots" {:url "https://repo.clojars.org"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]
                        ["releases"  {:url "https://repo.clojars.org"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]]
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["shell" "git" "commit" "-am" "Version ${:version} [ci skip]"]
                  ["vcs" "tag" "v" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["shell" "git" "commit" "-am" "Version ${:version} [ci skip]"]
                  ["vcs" "push"]]
  :plugins [[lein-shell "0.5.0"]]
  :profiles {:dev {:global-vars {*warn-on-reflection* true}
                   :source-paths ["dev"]
                   :resource-paths ["test-resources"]
                   :dependencies [[org.clojure/tools.namespace "1.2.0"]]}}
  :repl-options {:init-ns user})
