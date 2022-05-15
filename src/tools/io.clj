(ns tools.io
  (:refer-clojure :rename {slurp core-slurp
                           spit  core-spit})
  (:require
   [charred.api :as charred]
   [clj-yaml.core :as yaml]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [tools.io.core :as core])
  (:import
   [java.io BufferedReader BufferedWriter File]
   [java.net URL]
   [java.nio.file Path Files]
   [java.nio.file.attribute FileAttribute]))


;todo: use https://github.com/pjstadig/reducible-stream/

(def ^:private bom "\uFEFF")

(defn- lstrip
  [^String s ^String prefix]
  (if (str/starts-with? s prefix)
    (.substring s (count prefix))
    s))

(defn- rstrip
  [^String s ^String suffix]
  (if (str/ends-with? s suffix)
    (.substring s 0 (- (count s) (count suffix)))
    s))

(defn- strip
  [^String s ^String trim]
  (-> s
      (lstrip trim)
      (rstrip trim)))

(defn strip-bom
  "Remove the BOM on a string if necessary.
   See https://en.wikipedia.org/wiki/Byte_order_mark"
  {:added "0.3.16"}
  [^String s]
  ;; Inspired by http://stackoverflow.com/a/13789414/735926
  (lstrip s bom))

(defn- split-protocol
  [path]
  {:pre [(string? path)]}
  (let [[prefix x] (str/split path #"://" 2)]
     (if x
       [prefix x]
       [nil prefix])))

(defn- join-protocol
  [protocol path]
  (if (not-empty protocol)
    (str protocol "://" path)
    path))

(defn join-path
  "Join multiple parts of a path into one.

   (join-path \"gs://foo\" \"bar\")  ; => \"gs://foo/bar\"
   (join-path \"gs://foo/\" \"bar\") ; => \"gs://foo/bar\""
  {:added "0.3.16"}
  ([x]
   {:pre [(string? x)]}
   x)
  ([x & xs]
   {:pre [(string? x) (every? string? xs)]
    :post [(string? %)]}
   (let [[prefix x] (split-protocol x)
         is-root (str/starts-with? x "/")
         parts (map (fn [part] (strip part "/")) (cons x xs))
         path (str (when is-root "/") (str/join "/" (map io/file parts)))]

     (join-protocol prefix path))))

(defn basename
  "Return the basename of a file path

   (basename \"/foo/bar.gz\") ; => \"bar.gz\"
   (basename \"http://www.foo.com/qux/\") ; => \"qux\""
  {:added "0.3.16"}
  [path]
  (.getName (io/file path)))

(defn parent
  "Return the parent of a path.

   Note it doesn't resolve symbolink links nor `.` nor `..`."
  {:added "0.3.16"}
  [path]
  (let [[prefix path] (split-protocol path)
        parent-path (.getParent ^File (io/file path))]
    (join-protocol prefix parent-path)))

(defn splitext
  "Split a file path in a pair of (file-path-without-extension, extension)."
  {:added "0.3.16"}
  [path]
  (if-let [[_ path ext] (re-matches #"(.+)\.([^./]+)$" path)]
    [path ext]
    [path ""]))

(let [homedir (System/getProperty "user.home")]
  (defn expand-home
    {:added "0.3.16"}
    [path]
    (cond
    (= path "~") homedir
    (str/starts-with? path "~/") (join-path homedir (subs path 2 (count path)))
    :else path)))


(defn- lazy-lines-read
  [{:keys [^BufferedReader stream] :as file}]
  (lazy-seq
    (if-let [line (.readLine stream)]
      (cons line (lazy-lines-read file))
      (do (core/close! file) nil))))

(defn line-write
  "Write one line in a file created with tools.io.core/file-writer. It is the
   caller's responsibility to close the file using tools.io.core/close! when
   it’s done."
  {:added "0.3.16"}
  [{:keys [^BufferedWriter stream] :as _file} line]
  (.write stream ^String line)
  (when-not (str/ends-with? line "\n")
    (.write stream "\n"))
  nil)

(defn lines-write
  "Write lines in a file created with tools.io.core/file-writer and close it"
  {:added "0.3.16"}
  [file lines]
  (try
    (doseq [line lines] (line-write file line))
    (finally
      (core/close! file))))

(def ^{:doc "Alias of tools.io.core/list-files for backward compatibility."
       :arglists '([path & [options]])} list-files core/list-files)

(def ^{:doc "Alias of tools.io.core/list-dirs."
       :added "0.3.22"
       :arglists '([path & [options]])}
  list-dirs tools.io.core/list-dirs)

(defn read-string-format-file-fn
  "Builder for a file-reader function."
  [read-fn]
  (fn read-string-format-file
    ([filename] (read-string-format-file filename {}))
    ([filename options]
      (let [file (core/file-reader filename options)]
        (->> (lazy-lines-read file)
             (remove str/blank?)
             (map-indexed
                (fn apply-read-fn [idx line]
                  (try
                    (read-fn (if (= idx 0) (strip-bom line) line))
                    (catch Exception e
                      (core/close! file)
                      (throw (Exception. (format "error parsing file %s at line %d\nline=[%s]\n%s"
                                                 filename (inc idx) line (.getMessage e)))))))))))))

(defn read-string-files-fn
  "Builder for a files-reader function. This is equivalent to
   read-string-format-file-fn but the returned function expects a sequence of
   filenames instead of just one."
  [read-file-fn]
  (fn read-string-files
    ([filenames] (read-string-files filenames {}))
    ([filenames options]
      (lazy-seq
        (when (seq filenames)
          (concat (read-file-fn (first filenames) options)
                  (read-string-files (next filenames) options)))))))

(defn write-string-file-fn
  "Builder for a file-writer function. This is the opposite of
   read-string-format-file-fn."
  [serialize-fn]
  (fn write-string-file
    ([filename xs] (write-string-file filename {} xs))
    ([filename options xs]
     (let [file (core/file-writer filename options)]
       (->> xs
            (map-indexed (fn [idx x]
                           (try
                             (serialize-fn x)
                           (catch Exception e
                             (core/close! file)
                             (throw (Exception. (format "error serializing %s in file %s line %d\n%s"
                                                        (prn-str x) filename (inc idx) (.getMessage e))))))))
           (lines-write file))))))

(defn slurp
  "Equivalent of clojure.core/slurp"
  {:added "0.3.16"}
  [filename & opts]
  (apply core-slurp (:stream (core/file-reader filename)) opts))

(defn spit
  "Equivalent of clojure.core/spit"
  {:added "0.3.16"}
  [filename content & opts]
  (apply core-spit (:stream (core/file-writer filename)) content opts))


(def ^{:added "0.3.16"
       :doc
"return a lazy seq of string from a [protocol://]text[.gz] file.
 warning: the seq must be entirely consumed before the file is closed."}
  read-text-file
  (read-string-format-file-fn identity))

(def ^{:added "0.3.16"
       :doc
"write a seq of strings in a [protocol://]text[.gz] file."}
  write-text-file
  (write-string-file-fn identity))

(def ^{:added "0.3.16"
       :doc
"return a lazy seq of parsed json objects from a [protocol://]jsons[.gz] file.
 warning: the seq must be entirely consumed before the file is closed."}
  read-jsons-file
  (read-string-format-file-fn #(charred/read-json % :key-fn keyword)))

(def ^{:added "0.3.16"
       :doc
"write a seq of elements serialized as JSON in a [protocol://]jsons[.gz] file."}
  write-jsons-file
  (write-string-file-fn #(charred/write-json-str % :indent-str nil)))

(def ^{:added "0.3.16"
       :doc
"return a lazy seq of parsed edn objects from a [protocol://]edn[.gz] file.
 warning: the seq must be entirely consumed before the file is closed."}
  read-edns-file
  (read-string-format-file-fn edn/read-string))

(def ^{:added "0.3.16"
       :doc
"write a seq of elements serialized as EDN in a [protocol://]edn[.gz] file."}
  write-edns-file
  (write-string-file-fn prn-str))

(defn write-edn-file
  "Write an element serialized as EDN in a [protocol://]edn[.gz] file.
   This is equivalent to call write-edns-file on a one-element sequence."
  ([filename x] (write-edns-file filename [x]))
  ([filename options x] (write-edns-file filename options [x])))

(defn ^{:added "0.3.16"
        :doc
"return a lazy seq of parsed csv row as vector from a [protocol://]csv[.gz] file.
 see http://clojure.github.io/data.csv/ for options
 warning: the seq must be entirely consumed before the file is closed.

 sample usage:
 (read-csv-file \"infos_tarifs.csv\" {:encoding \"ISO-8859-1\"} :separator \\;)
 "}
  read-csv-file
  ([in]
   (read-csv-file in nil))
  ([in stream-args & csv-args]
   (let [file (core/file-reader in stream-args)]
     (apply charred/read-csv (:stream file) csv-args))))

(defn- write-csv-file*
  [out stream-args csv-args]
  (let [file (core/file-writer out stream-args)]
    (try
      (apply charred/write-csv (:stream file) csv-args)
      (finally
        (core/close! file)))))

(defn ^{:added "0.3.16"
        :doc
"write a seq of vectors serialized as CSV in a [protocol://]csv[.gz] file.
 see http://clojure.github.io/data.csv/ for options.

 (write-csv-file out my-lines)
 (write-csv-file out [stream-options-map] my-lines [csv options...])

 Examples:
  (write-csv-file \"animals.csv\" [[\"name\" \"color\"] [\"cat\" \"black\"] [\"dog\" \"brown\"]])
  (write-csv-file \"animals.csv\" [[\"name\" \"color\"] [\"cat\" \"black\"] [\"dog\" \"brown\"]] :separator \\;)

  (write-csv-file \"people.csv\" {:encoding \"ISO-88591\"} my-people)
  (write-csv-file \"people.csv\" {:encoding \"ISO-88591\"} my-people :separator \\;)
"}
  write-csv-file
  [out stream-args & csv-args]
  (if (map? stream-args)
    ;; (write-csv-file "foo.csv" {} xs ...)
    (write-csv-file* out stream-args csv-args)
    ;; (write-csv-file "foo.csv" xs ...)
    (write-csv-file* out nil (cons stream-args csv-args))))

(def ^{:added "0.3.16"
       :doc
"return a lazy seq of parsed json objects from [protocol://]jsons[.gz] files.
 warning: the seq must be entirely consumed before every files are closed."}
  read-jsons-files
  (read-string-files-fn read-jsons-file))

(def ^{:added "0.3.16"
       :doc
"return a lazy seq of parsed edn objects from [protocol://]edns[.gz] files.
 warning: the seq must be entirely consumed before every files are closed."}
  read-edns-files
  (read-string-files-fn read-edns-file))

(def ^{:private true}
  config-parser {:edn  edn/read-string
                 :clj  read-string
                 :json #(charred/read-json % :key-fn keyword)
                 :yaml yaml/parse-string})

(def ^{:private true}
  config-exts-equivalence {:js :json
                           :yml :yaml})

(defn as-file!
  "coerce argument to an io/file.
   argument could be a string, an io/file or an io/resource"
  [f]
  (cond
    (instance? URL f) (io/input-stream f)
    (instance? File f) f
    :else (let [r (io/resource (str f))]
            (if (nil? r) (io/file (str f))
                         (io/input-stream r)))))

(defn ->file-path
  [filename]
  (cond
    (instance? URL filename) (.getFile ^URL filename)
    (instance? File filename) (.getPath ^File filename)
    :else (str filename)))

(defn- fail-as-nil [f] (try (f) (catch Exception _ nil)))

(defn load-config-file
  "read and parse a configuration file
   edn, clj, json, js, yaml, yml supported
   protocols supported are those of `tools.io.core/file-reader`"
  {:added "0.3.16"}
  ([filename]
   (when (some? filename)
     (let [path     (->file-path filename)
           ext      (keyword (str/lower-case (last (str/split path #"\."))))
           parse-fn (get config-parser (or (ext config-exts-equivalence) ext))]
       (load-config-file filename parse-fn))))
  ([filename parser]
   {:pre [parser]}
    (let [path     (->file-path filename)
          raw      (or (fail-as-nil #(slurp filename))
                       (fail-as-nil #(-> filename as-file! slurp))) ]
      (when raw
        (try
          (parser raw)
          (catch Exception e
            (throw (Exception. (str "error parsing config file " path ".\n" (.getMessage e))))))))))


(letfn [(close-all [files] (doseq [f files] (core/close! f)))]
  (defn copy
    "Copy content of file to another file. The only key for copy-opts is :buffer-size that precises the buffer size
    used between reader and writer.
    This function may not be the optimal way to copy a file."
    {:added "0.3.16"}
    ([from from-opts to to-opts]
     (copy from from-opts to to-opts nil))
    ([from from-opts to to-opts copy-opts]
     (let [[reader writer :as files] [(core/file-reader from from-opts) (core/file-writer to to-opts)]]
       (try
         (core/copy (:stream reader) (:stream writer) copy-opts)
         (catch Exception e (throw (ex-info (format "error copying %s in %s"
                                                    (prn-str from) (prn-str to))
                                            {:from      from
                                             :from-opts from-opts
                                             :to        to
                                             :to-opts   to-opts
                                             :copy-opts copy-opts}
                                            e)))
         (finally (close-all files)))))))


(defprotocol RmRfProtocol
  (^{:added "0.3.16"}
   rm-rf
    [path]
    "Recursively remove a directory"))

(extend-protocol RmRfProtocol
  String (rm-rf [path] (rm-rf (File. path)))
  Path   (rm-rf [path] (rm-rf (.toFile path)))
  File   (rm-rf [f]
           (when (.isDirectory f)
             (doseq [child (.listFiles f)]
               (rm-rf child)))
           (.delete ^File f)))

(defn with-tempfile-impl
  {:added "0.3.16"}
  [f]
  (let [temp (File/createTempFile "tools-common" ".tmp")]
    (try
      (.deleteOnExit temp)
      (f (.getPath temp))
      (finally
        (.delete ^File temp)))))

(defmacro with-tempfile
  "Executes the body with a temporary file whose name is bind to [x]:

    (with-tempfile [filename]
       (println \"There's a file called\" filename \".\"))
    (println \"The file is now gone.\")"
  {:added "0.3.16"}
  [[x] & body]
  `(with-tempfile-impl (fn ~[x] ~@body)))


(defn with-tempdir-impl
  {:added "0.3.16"}
  [f]
  (let [path (Files/createTempDirectory "tools-common" (make-array FileAttribute 0))
        temp (.toFile path)]
    (try
         (.deleteOnExit temp) ; note it won't work if there are files in it
         (f (str path))
         (finally
           (rm-rf temp)))))

(defmacro with-tempdir
  "Executes the body with a temporary directory whose name is bind to [x]:

    (with-tempdir [dirname]
       (println \"There's a directory called\" dirname \".\"))
    (println \"The directory is now gone.\")"
  {:added "0.3.16"}
  [[x] & body]
  `(with-tempdir-impl (fn ~[x] ~@body)))

(defn exists?
  "Return true if a file exists

   (exists? \"--i-dont-exists--\") ; => false
   (exists? \"https://www.oscaro.com/\") ; => true"
  {:added "0.3.17"}
  [filename & [options]]
  (core/exists? filename options))
