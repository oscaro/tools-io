(defproject com.oscaro/tools-io "0.3.17"
  :description "Oscaro's generic I/O tools collection"
  :url "https://github.com/oscaro/tools-io"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [cheshire "5.8.1"]
                 [circleci/clj-yaml "0.6.0"]
                 [org.clojure/data.csv "0.1.4"]]
  :deploy-repositories [["releases" {:url "https://clojars.org/repo"}]]
  :profiles {:dev {:global-vars {*warn-on-reflection* true}
                   :source-paths ["dev"]
                   :resource-paths ["test-resources"]
                   :dependencies [[org.clojure/tools.namespace "0.2.11"]]}}
  :repl-options {:init-ns user})
