(ns tools.io.core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [tools.io.compress :as zio])
  (:import
   (java.io ByteArrayOutputStream Closeable File Reader Writer)
   (java.util.zip ZipEntry ZipOutputStream)
   (org.apache.commons.compress.archivers.zip ZipArchiveEntry ZipFile)))

(defonce ^:private file-preds (atom {}))

(defn register-file-pred!
  "Registers a new file type with a file predicate.

   Extends the dispatch function of the multimethods with your own
   implementation.

   Example:
     (register-file-pred!
       :my-filetype
       (fn [filename]
        (clojure.string/starts-with? (str filename) \"myprotocol://\")))

     (defmethod mk-input-stream :my-filetype
       [filename & [options]]
       ;; ...
       {:stream ...})

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
       (throw (Exception.
               (str "Unsupported protocol for " filename "."
                    " Perhaps you forgot to require the proper extension"
                    " (eg.: tools.io.gs)")))))
  ([filename _ & _]
   (get-file-type filename)))

(defn- mk-file-protocol-checker
  [protocols]
  {:pre [(set? protocols)]}
  (fn [filename]
    (when-not (instance? Reader filename)
      (-> filename
          str
          (str/split #"://" 2)
          first
          str/lower-case
          protocols))))

(defmulti mk-input-stream
  "Returns an input stream with any implementation."
  get-file-type)

(defmulti mk-output-stream
  "Returns an output stream with any implementation."
  get-file-type)

(defn- file-ext
  [filename]
  (second (re-find #"\.([^./]+)$" (str filename))))

(defn input-stream
  "Returns an input-stream for plain or compressed file."
  ([filename] (input-stream filename nil))
  ([filename options]
   (let [is (mk-input-stream filename options)
         compressor (zio/get-compressor (file-ext filename))]
     (if compressor
       (update is :stream #(zio/-get-input-stream compressor % options))
       is))))

(defn output-stream
  "Returns an output-stream for plain or compressed file."
  ([filename] (output-stream filename nil))
  ([filename options]
   (let [os (mk-output-stream filename options)
         compressor (zio/get-compressor (file-ext filename))]
     (if compressor
       (update os :stream #(zio/-get-output-stream compressor % options))
       os))))

(defn file-reader
  "Returns a file reader."
  ([filename] (file-reader filename nil))
  ([filename options]
   (let [file (input-stream filename options)]
     (update file :stream #(io/reader % :encoding (:encoding options "UTF-8"))))))

(defn file-writer
  "Returns a file writer."
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

(defmulti list-dirs
  "Returns a seq of directories with provided path as prefix."
  get-file-type)

(defmulti delete-file
  "Deletes a file with any implementation."
  get-file-type)

(defmulti exists?
  "Returns `true` if filename exists."
  get-file-type)

(defmulti zip-directory
  "Creates zip from target directory."
  get-file-type)

(defmulti unzip-file
  "Unzip the targeted file to the current directory.
   If not a Zip file, yield `nil`."
  get-file-type)

(defmulti sizeof
  "Probe the size of the target files/folder.
   Should not load dataset in memory."
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

(defn ^:no-doc ->byte-array
  [input-stream]
  (let [bao (ByteArrayOutputStream.)]
    (io/copy input-stream bao)
    (.toByteArray bao)))

;; Default Hooks
;; =============

;; On-Disk Files
;; -------------

(register-file-pred!
 :base (some-fn
        (mk-file-protocol-checker #{"file"})
        (fn filename-with-no-protocol?
          [filename]
          (and
           (not (instance? Reader filename))
           (not (str/includes? (str filename) "://"))))))

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

(defmethod list-dirs :base
  [path & [_options]]
  (let [^File f (io/file path)]
    (when (.isDirectory f)
      (->> (.listFiles f)
           (filter #(.isDirectory ^File %))
           (map #(.getPath ^File %))))))

(defmethod delete-file :base
  [path & [options]]
  (io/delete-file path (:silently options false)))

(defmethod exists? :base
  [filename & [_options]]
  (when filename
    (try
      (.exists (io/as-file filename))
      (catch Exception _ false))))

;; Zip disk primitives

(defmethod zip-directory :base
  [folder & {:keys [output-file
                    absolute?]
             :or {absolute? true}}]
  (when output-file
    (try
      (with-open [zip (ZipOutputStream. (io/output-stream output-file))]
        (let [folder  (if absolute? folder
                          (.getName (io/file folder)))
              files (file-seq (io/file folder))]
          (doseq [f files
                  :when (.isFile  ^File f)]
            (.putNextEntry zip (ZipEntry. (.getPath ^File f)))
            (io/copy f zip)
            (.closeEntry zip))))
      true
      (catch Exception _ false))))

(defmethod unzip-file :base
  [filename & {:keys [output-folder overwrite?]
               :or {overwrite? false}}]
  (let [extension (str/lower-case (last (str/split filename #"\.")))]
    (try
      (when (and output-folder (= extension "zip"))
        (with-open [z (ZipFile. (io/file filename))]
          (doseq [^ZipArchiveEntry entry (enumeration-seq (.getEntries z))
                  :when (not (.isDirectory ^ZipArchiveEntry entry))
                  :let  [zs (.getInputStream z entry)
                         out (io/file (str output-folder
                                           java.io.File/separator
                                           (.getName entry)))]]
            (io/make-parents out)
            (when (or (not (.exists out)) overwrite?)
              (with-open [entry-o-s (io/output-stream out)]
                (io/copy zs entry-o-s))))
          true))
      (catch Exception _ false))))

;; File size probe

(defmethod sizeof :base
  [target & _opts]
  (letfn [(size [^File p]
            (if (.isDirectory p)
              (apply + (pmap size (.listFiles p)))
              (.length p)))]
    (when-let [target (io/file target)]
      (when (.exists target)
        (size target)))))

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
