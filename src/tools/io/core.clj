(ns tools.io.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io File Closeable Reader Writer]
           [java.util.zip GZIPInputStream GZIPOutputStream])
  (:gen-class))

(defonce file-preds (atom {}))

(defn register-file-pred!
  "Extend the dispatch function of the multimethods with your own
   implementation.

   Example:

     (register-file-pred!
       :my-filetype
       (fn [filename] (clojure.string/starts-with? (str filename) \"myprotocol://\")))

     (defmethod mk-input-stream :my-filetype
       [filename & [options]]
       ;; ...
       {:stream ...}
       )

   The code above is sufficient to get all reading function for free on your
   custom protocol, given that mk-input-stream works correctly."
  [file-type file-pred]
  (swap! file-preds assoc file-type file-pred))

(defn unregister-file-pred!
  [file-type]
  (swap! file-preds dissoc file-type))

(defn get-file-type
  "Dispatch function for files IO multimethods."
  ([filename]
   (or (some (fn [[file-type file-pred]]
               (when (file-pred filename)
                 file-type)) @file-preds)
       (throw (Exception. (str "unsupported protocol for " filename ". perhaps you forgot to require the proper extension (eg.: tools.io.gs)")))))
  ([filename _ & _]
   (get-file-type filename)))

(defn- mk-file-protocol-checker
  [protocols]
  {:pre [(set? protocols)]}
  (fn [filename]
    (when-not (instance? java.io.Reader filename)
      (let [filename (str filename)]
        (or (not (str/includes? filename "://"))
            (-> filename
                (str/split #"://" 2)
                first
                str/lower-case
                protocols))))))

(defmulti mk-input-stream
  "Returns an input stream with any implementation."
  get-file-type)

(defmulti mk-output-stream
  "Returns an output stream with any implementation."
  get-file-type)

(defn gzipped?
  "Tests if a filename ends with .gz or .gzip"
  [filename]
  (re-find #"(?i)\.gz(?:ip)?$" (str filename)))

(defn input-stream
  "Returns an input-stream, with support of gzip compression."
  ([filename] (input-stream filename nil))
  ([filename options]
   (let [is (mk-input-stream filename options)]
     (if (gzipped? filename)
       (update is :stream #(GZIPInputStream. %))
       is))))

(defn output-stream
  "Returns an output-stream, with support of gzip compression."
  ([filename] (output-stream filename nil))
  ([filename options]
   (let [os (mk-output-stream filename options)]
     (if (gzipped? filename)
       (update os :stream #(GZIPOutputStream. %))
       os))))

(defn file-reader
  "Return a file reader"
  ([filename] (file-reader filename nil))
  ([filename options]
   (let [file (input-stream filename options)]
     (update file :stream #(io/reader % :encoding (:encoding options "UTF-8"))))))

(defn file-writer
  "Return a file writer"
  ([filename] (file-writer filename nil))
  ([filename options]
   (let [file (output-stream filename options)]
     (update file :stream #(io/writer % :encoding (:encoding options "UTF-8"))))))

(defn close!
  "Close a stream and optionally call its close-fn function if it's present."
  [{:keys [^Closeable stream close-fn] :as file}]
  (.close stream)
  (when close-fn (close-fn file)))

(defmulti list-files
  "Returns a seq of filenames with provided path as prefix."
  get-file-type)

(defmulti delete-file
  "Deletes a file with any implementation."
  get-file-type)

(defmulti exists?
  "Returns `true` if filename exists"
  get-file-type)


;;Shamefully copied from clojure.java.io/do-copy because we hardly can reuse the do-copy multi-fn
(defn copy
  [^Reader input ^Writer output opts]
  (let [^"[C" buffer (make-array Character/TYPE (:buffer-size opts 4096))]
    (loop []
      (let [size (.read input buffer)]
        (when (pos? size)
          (.write output buffer 0 size)
          (recur))))))

;; Default Hooks
;; =============

;; On-Disk Files
;; -------------

(register-file-pred!
  :base (mk-file-protocol-checker #{"file" ""}))

(defmethod mk-input-stream :base
  [filename & [options]]
  {:stream (io/input-stream filename :encoding (:encoding options "UTF-8"))})

(defmethod mk-output-stream :base
  [filename & [options]]
  {:stream (io/output-stream filename :append (:append options false))})

(defmethod list-files :base
  [path & [_options]]
  (let [^File f (io/file path)]
    (cond
      (.isDirectory f)
        (->> (file-seq f)
             (filter #(.isFile ^File %))
             (map #(.getPath ^File %)))

      ;; trailing slash but not a directory: nil
      (str/ends-with? path File/separator)
        nil

      :else
        (when-let [parent (.getParentFile f)]
          (let [prefix (.getName f)]
            (->> (.listFiles parent)
                 (filter (fn [^File file]
                           (and (.isFile file)
                                (str/starts-with? (.getName file) prefix))))
                 (map (fn [^File f] (.getPath f)))))))))

(defmethod list-folders :base
  [path & [_options]]
  (let [^File f (io/file path)]
    (cond
      (.isDirectory f)
        (->> (file-seq f)
             (filter #(.isDirectory ^File %))
             (map #(.getPath ^File %)))

       ;; trailing slash but not a directory: nil
       (str/ends-with? path File/separator)
       nil

       :else nil)))

(defmethod delete-file :base
  [path & [options]]
  (io/delete-file path (:silently options false)))


(defmethod exists? :base
  [filename & [_options]]
  (when filename
    (try
      (.exists (io/as-file filename))
      (catch Exception _ false))))


;; HTTP & HTTPS
;; ------------
;; Basic implementation.

(register-file-pred!
  :http (mk-file-protocol-checker #{"http" "https"}))

(defmethod mk-input-stream :http
  [filename & [options]]
  {:stream (io/input-stream filename :encoding (:encoding options "UTF-8"))})

(defmethod exists? :http
  [filename & [options]]
  (when filename
    (try
      (with-open [stream (io/input-stream filename :encoding (:encoding options "UTF-8"))]
        (pos? (.available stream)))
      (catch Exception _ false))))


;; STDIN
;; -----

(register-file-pred!
  :stdin (fn [filename]
           (= *in* filename)))

(defmethod mk-input-stream :stdin
  [filename & [_options]]
  {:stream filename :close-fn (fn noop [_])})
