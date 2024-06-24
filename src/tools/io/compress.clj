(ns tools.io.compress
  (:require
   [clojure.string :as str])
  (:import
   (java.io InputStream OutputStream)
   (org.apache.commons.compress.compressors CompressorException CompressorStreamFactory)
   (org.apache.commons.compress.compressors.bzip2 BZip2CompressorInputStream BZip2CompressorOutputStream)
   (org.apache.commons.compress.compressors.gzip GzipCompressorInputStream GzipCompressorOutputStream GzipParameters)
   (org.apache.commons.compress.compressors.lz4 FramedLZ4CompressorInputStream FramedLZ4CompressorOutputStream FramedLZ4CompressorOutputStream$BlockSize FramedLZ4CompressorOutputStream$Parameters)
   (org.apache.commons.compress.compressors.xz XZUtils)
   (org.apache.commons.compress.compressors.zstandard ZstdUtils)))

(defn ^:no-doc detect?
  "Do not expose!
   Useful for internal use but depends too much on Common Compress."
  [input-stream]
  (try
    (CompressorStreamFactory/detect input-stream)
    (catch CompressorException e
      (println (ex-message e)))))

(defprotocol Compressor
  "Interface of a Compressor implementation usable with `tools.io`."

  (-get-file-extensions
    [_this]
    "Returns a collection of related file extensions.")
  (-get-input-stream ^InputStream
    [_this input-stream options]
    "Returns a stream which uncompress an input stream.")
  (-get-output-stream ^OutputStream
    [_this output-stream options]
    "Returns a stream which compress an output stream."))

(defonce ^:private ^:no-doc !ext->compressor (atom {}))

(defn register-compressor!
  "Registers a new compressor implementation in the global registry."
  [compressor]
  (if (satisfies? Compressor compressor)
    (let [exts (-get-file-extensions compressor)]
      (doseq [ext (mapv str/lower-case exts)]
        (swap! !ext->compressor assoc ext compressor)))
    (throw (ex-info (str "Invalid compressor object:" compressor) {}))))

(defn unregister-compressor!
  "Unregisters a compressor from the global registry."
  [compressor]
  (if (satisfies? Compressor compressor)
    (let [exts (-get-file-extensions compressor)]
      (doseq [ext (mapv str/lower-case exts)]
        (swap! !ext->compressor dissoc ext)))
    (throw (ex-info (str "Invalid compressor object:" compressor) {}))))

(defn get-compressor
  "Returns the compressor associated with a given file extension or nil."
  [file-extension]
  (when (string? file-extension)
    (get @!ext->compressor (str/lower-case file-extension))))

;;
;; Gzip support
;;

(defn- gzip-opts
  [{:keys [compression-level buffer-size]}]
  (let [params (GzipParameters.)]
    (when compression-level
      (.setCompressionLevel params (int compression-level)))
    (when buffer-size
      (.setBufferSize params (int buffer-size)))
    params))

(defrecord GzipCompressor []
  Compressor
  (-get-file-extensions
    [_]
    ["gz" "gzip"])
  (-get-input-stream
    [_ input-stream {:keys [concatenated?]}]
    (GzipCompressorInputStream. input-stream (boolean concatenated?)))
  (-get-output-stream
    [_ output-stream opts]
    (GzipCompressorOutputStream. output-stream (gzip-opts opts))))

;;
;; Bzip2 support
;;

(defrecord Bzip2Compressor []
  Compressor
  (-get-file-extensions
    [_]
    ["bz2" "bzip2"])
  (-get-input-stream
    [_ input-stream {:keys [concatenated?]}]
    (BZip2CompressorInputStream. input-stream (boolean concatenated?)))
  (-get-output-stream
    [_ output-stream {:keys [block-size]}]
    (if block-size
      (BZip2CompressorOutputStream. output-stream (int block-size))
      (BZip2CompressorOutputStream. output-stream))))

;;
;; Framed LZ4 support
;;

(defn- ->lz4-block-size
  [s]
  (FramedLZ4CompressorOutputStream$BlockSize/valueOf (name s)))

(defn- lz4-opts
  [{:keys [block-size]}]
  (if block-size
    (FramedLZ4CompressorOutputStream$Parameters. (->lz4-block-size block-size))
    FramedLZ4CompressorOutputStream$Parameters/DEFAULT))

(defrecord FramedLZ4Compressor []
  Compressor
  (-get-file-extensions
    [_]
    ["lz4"])
  (-get-input-stream
    [_ input-stream {:keys [concatenated?]}]
    (FramedLZ4CompressorInputStream. input-stream (boolean concatenated?)))
  (-get-output-stream
    [_ output-stream opts]
    (FramedLZ4CompressorOutputStream. output-stream (lz4-opts opts))))

;;
;; Extra provided compression
;;

(defn- ex-compression
  [compression]
  (let [msg (format "%s compression is not available"
                    (str/upper-case compression))]
    (ex-info msg {:error msg :missing-compression compression})))

;;
;;
;; Zstandard support
;;

(defmacro ^:private when-zstd-provided
  [& body]
  (if (ZstdUtils/isZstdCompressionAvailable)
    `(do ~@body)
    `(throw (ex-compression "Zstd"))))

(defmacro ^:private zstd-new
  [stream-name & args]
  `(new ~(symbol (str "org.apache.commons.compress.compressors.zstandard."
                      (name stream-name)))
        ~@args))

(defrecord ZstdCompressor []
  Compressor
  (-get-file-extensions
    [_]
    ["zst" "zstd"])
  (-get-input-stream
    [_ input-stream _opts]
    (when-zstd-provided
     (zstd-new "ZstdCompressorInputStream" input-stream)))
  (-get-output-stream
    [_ output-stream {:keys [level]}]
    (when-zstd-provided
     (if level
       (zstd-new "ZstdCompressorOutputStream" output-stream (int level))
       (zstd-new "ZstdCompressorOutputStream" output-stream)))))

;;
;; XZ support
;;

(defmacro ^:private when-xz-provided
  [& body]
  (if (XZUtils/isXZCompressionAvailable)
    `(do ~@body)
    `(throw (ex-compression "XZ"))))

(defmacro ^:private xz-new
  [stream-name & args]
  `(new ~(symbol (str "org.apache.commons.compress.compressors.xz."
                      (name stream-name)))
        ~@args))

(defrecord XZCompressor []
  Compressor
  (-get-file-extensions
    [_]
    ["xz"])
  (-get-input-stream
    [_ input-stream {:keys [concatenated?]}]
    (when-xz-provided
     (xz-new "XZCompressorInputStream" input-stream (boolean concatenated?))))
  (-get-output-stream
    [_ output-stream {:keys [preset]}]
    (when-xz-provided
     (if preset
       (xz-new "XZCompressorOutputStream" output-stream (int preset))
       (xz-new "XZCompressorOutputStream" output-stream)))))

;;
;; Register default compressors
;;

(def ^:private default-compressors
  #{(->GzipCompressor)
    (->Bzip2Compressor)
    (->ZstdCompressor)
    (->XZCompressor)
    (->FramedLZ4Compressor)})

(defn register-default-compressors!
  "Register all default compressors."
  []
  (doseq [compressor default-compressors]
    (register-compressor! compressor)))

(defn unregister-default-compressors!
  "Unregister all default compressors."
  []
  (doseq [compressor default-compressors]
    (unregister-compressor! compressor)))

(register-default-compressors!)
