(ns user
  (:refer-clojure :rename {slurp core-slurp
                           spit  core-spit})
  (:require [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [clojure.java.javadoc :refer [javadoc]]
            [clojure.pprint :refer [pprint]]
            [clojure.reflect :refer [reflect]]
            [clojure.repl :refer [apropos dir doc find-doc pst source]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [tools.io.core :as core]
            [tools.io :as tio]))
