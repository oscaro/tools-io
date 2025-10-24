(defproject com.oscaro/tools-io "0.3.42"
  :description "Oscaro's generic I/O tools collection"
  :url "https://github.com/oscaro/tools-io"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure                 "1.11.4"]
                 [clj-commons/clj-yaml                "1.0.29"]
                 [com.cnuernber/charred               "1.037"]
                 [org.apache.commons/commons-compress "1.28.0"]]
  ;; https://www.blog.nodrama.io/travis-continuous-delivery/
  ;; https://github.com/technomancy/leiningen/issues/2307#issuecomment-610538144
  :deploy-repositories [["snapshots" {:url "https://repo.clojars.org"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases false}]
                        ["releases"  {:url "https://repo.clojars.org"
                                      :username :env/clojars_username
                                      :password :env/clojars_password
                                      :sign-releases true}]]
  :signing {:gpg-key "github-cicd@oscaro.com"}
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
                   :resource-paths ["test/resources"]
                   :dependencies [[org.clojure/tools.namespace "1.5.0"]]}
             :extra-compression
             {:dependencies [[org.tukaani/xz            "1.10"]
                             [com.github.luben/zstd-jni "1.5.7-6"]]}}
  :test-selectors {:default (fn [m] (not (:extra-compression m)))
                   :extra-compression :extra-compression}
  :aliases {"repl" ["with-profile" "+extra-compression" "repl"]
            "test-all" ["with-profile" "+extra-compression" "test" ":all"]}
  :target-path "target/%s/"
  :repl-options {:init-ns user})
