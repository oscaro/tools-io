(defproject com.oscaro/tools-io "0.3.19-SNAPSHOT"
  :description "Oscaro's generic I/O tools collection"
  :url "https://github.com/oscaro/tools-io"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [cheshire "5.9.0"]
                 [circleci/clj-yaml "0.6.0"]
                 [org.clojure/data.csv "0.1.4"]]
  ;; https://www.blog.nodrama.io/travis-continuous-delivery/
  :deploy-repositories [["snapshots" {:url "https://clojars.org/repo"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]
                        ["releases"  {:url "https://clojars.org/repo"
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
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]]}}
  :repl-options {:init-ns user})
