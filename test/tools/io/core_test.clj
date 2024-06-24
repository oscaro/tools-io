(ns tools.io.core-test
  (:require
   [charred.api :as charred]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [are deftest is testing]]
   [tools.io :as tio]
   [tools.io.core :as sut])
  (:import
   (java.io Closeable File)))

(deftest expand-home-test
  (let [home (System/getProperty "user.home")]
    (are [expected path] (= expected (tio/expand-home path))
      ""   ""
      home "~"
      home home
      (str home "/foo/bar") "~/foo/bar")))

(deftest read-txt-test
  (testing "reading resource test.txt file"
    (is (= "answer:42" (reduce str (tio/read-text-file (io/resource "test.txt")))))))

(deftest read-write-txt-test
  (let [text ["don't" "worry" "about" "the" "sardines."]
        filename ".read-write-tmp-test-rw-text"]
    (io/delete-file filename true)
    (try
      (tio/write-text-file filename text)
      (is (= text (tio/read-text-file filename)))
      (is (= (str/join "" (map #(str % "\n") text)) (tio/slurp filename)))
      (finally
        (io/delete-file filename true)))))

(deftest get-default-file-types-test
  (are [ftype filename] (= ftype (sut/get-file-type filename))
    :base "file://foo"
    :base "/foo"
    :base "../foo"
    :base "bar"
    :base "file://toto"
    :base "file:///toto"

    :http "http://www.google.com"
    :http "https://www.google.com"

    :stdin *in*))

(deftest custom-file-type-test
  (let [file-type :test1234
        filename "test1234://foobar"]
    (is (thrown? Exception (sut/get-file-type filename)))

    (try
      (sut/register-file-pred!
       file-type #(str/starts-with? (str %) "test1234://"))

      (is (= file-type (sut/get-file-type filename)))

      (finally
        (sut/unregister-file-pred! file-type)))))

(deftest mk-file-protocol-checker-test
  (let [checker (#'sut/mk-file-protocol-checker #{"foo" "bar"})]
    (are [ok-path] (checker ok-path)
      "foo://something"
      "bar://something"
      "FOO://something")
    (are [not-ok-path] (not (checker not-ok-path))
      "http://something"
      "/home/linus/SECRET"
      "hello")))

(deftest read-from-stdin-test
  (with-in-str "{\"toto\": 42}"
    (is (= [{:toto 42}]
           (tio/read-jsons-file *in*))))

  (testing "long line"
    (let [obj (vec (range 9000))
          s (charred/write-json-str obj)]
      (with-in-str s
        (is (= [obj] (tio/read-jsons-file *in*)))))))

(deftest copy-test
  (testing "copy from filesystem to filesystem"
    (let [from (File/createTempFile "tio-test-from" ".tmp")
          to (File/createTempFile "tio-test-to" ".tmp")]
      (try
        (.delete to)
        (spit from "écho" :encoding "iso-8859-1")
        (tio/copy (.getPath from) {:encoding "iso-8859-1"}
                  (.getPath to) {:encoding "utf-8"})
        (is (= "écho" (slurp to :encoding "utf-8")))
        (finally (.delete from) (.delete to))))))

(deftest list-files-test
  (testing "list files from an existant dir"
    (is (= 5 (count (tio/list-files "test/resources/test")))))
  (testing "list files from an existant file"
    (is (= "test/resources/test.txt"
           (first (tio/list-files "test/resources/test.txt")))))
  (testing "list files from an inexistant dir"
    (is (empty? (tio/list-files "-i do no exists-"))))

  (testing "list non-existant files"
    ;; in an earlier version this failed because /a doesn't exist but / does.
    ;; It thus recursively listed the files under / and yielded all those
    ;; starting with an 'a'.
    (is (empty? (take 1 (tio/list-files "/a")))))

  (tio/with-tempdir [dirname]
    ;; .
    ;; ├── aa
    ;; ├── aaa/
    ;; │   └── x
    ;; └── aab/
    ;;     └── x
    (let [files (->> ["aa"
                      "aaa/x"
                      "aab/x"]
                     (map #(format "%s/%s" dirname %)))]
      (doseq [path files]
        (io/make-parents path)
        (spit path ""))

      (testing "nested files"
        (testing "no match"
          (is (empty? (tio/list-files (str dirname "/x")))))

        (testing "files matches"
          ;; don't match directories
          (are [prefix] (= [(format "%s/aa" dirname)]
                           (sort (tio/list-files (tio/join-path dirname prefix))))
            "a"
            "aa"))

        (testing "trailing slash dir"
          (is (= [(format "%s/aaa/x" dirname)]
                 (vec (tio/list-files (tio/join-path dirname "aaa/"))))))

        (testing "trailing slash no dir"
          (is (empty? (tio/list-files (tio/join-path dirname "foo/"))))
          (is (empty? (tio/list-files (tio/join-path dirname "aaa/y/")))))))))

(deftest with-tempfile-test
  (let [*filename* (atom nil)
        content "this is my test yo"]

    (tio/with-tempfile [filename]
      (reset! *filename* filename)
      (is (.exists (io/as-file @*filename*)))

      (spit filename content)
      (is (= content (slurp filename))))

    (tio/with-tempfile [filename]
      (is (not= content (slurp filename))))

    (is (not (.exists (io/as-file @*filename*))))))

(deftest with-tempdir-test
  (let [*dirname* (atom nil)]
    (tio/with-tempdir [dirname]
      (reset! *dirname* dirname)

      (is (.exists (io/as-file @*dirname*)))
      (is (.isDirectory (io/as-file @*dirname*)))

      (io/make-parents (str dirname "/a/b/c/d"))
      (spit (str dirname "/foo") "yo"))

    (is (not (.exists (io/as-file @*dirname*))))))

(deftest exists?-test
  (testing "exists? with existant dir"
    (is (tio/exists? "test/resources/")))
  (testing "exists? with existant file"
    (is (tio/exists? "test/resources/test.txt")))
  (testing "exists? with an inexistant file"
    (is (not (tio/exists? "-i do no exists-")))))

(defmacro ^:private test-input-stream
  [file-ext]
  (let [path "compress/utf8-demo.txt"]
    `(with-open [expected-stream# (io/input-stream (io/resource ~path))
                 ^Closeable actual-stream# (-> ~(str path
                                                     (when (not= "plain" file-ext)
                                                       (str "." file-ext)))
                                               (io/resource)
                                               (sut/input-stream)
                                               :stream)]
       (is (= (vec (sut/->byte-array expected-stream#))
              (vec (sut/->byte-array actual-stream#)))
           ~(format "read %s input-stream" file-ext)))))

(deftest input-stream-test
  (testing "it deals with plain data"
    (test-input-stream "plain"))
  (testing "it deals with compressed data"
    (test-input-stream "gz")
    (test-input-stream "bz2")
    (test-input-stream "lz4")))

(deftest ^:extra-compression input-stream-extra-test
  (testing "it deals with compressed data (extra algorithms)"
    (test-input-stream "zst")
    (test-input-stream "xz")))

(defmacro ^:private test-output-stream
  [file-ext & [stream-opts]]
  (let [path "compress/utf8-demo.txt"]
    `(tio/with-tempdir [adir#]
       (let [afile# (tio/join-path adir# ~(str "out." file-ext))
             byte-arr# (with-open [in# (io/input-stream (io/resource ~path))]
                         (sut/->byte-array in#))]

         (with-open [^Closeable out# (-> afile#
                                         (sut/output-stream ~stream-opts)
                                         :stream)]
           (io/copy byte-arr# out#))

         (with-open [^Closeable in# (-> afile# (sut/input-stream) :stream)]
           (is (= (vec byte-arr#)
                  (vec (sut/->byte-array in#)))))))))

(deftest output-stream-test
  (testing "it uses uncompressed output-stream"
    (test-output-stream "dat"))

  (testing "it uses compressed output stream"
    (test-output-stream "gz")
    (test-output-stream "gz" {:compression-level 1})

    (test-output-stream "bz2")
    (test-output-stream "bz2" {:block-size 1})

    (test-output-stream "lz4")
    (test-output-stream "lz4" {:block-size :K64})))

(deftest ^:extra-compression output-stream-extra-test
  (testing "it uses compressed output stream (extra algorithms)"
    (test-output-stream "zst")
    (test-output-stream "zst" {:level 1})

    (test-output-stream "xz")
    (test-output-stream "xz" {:preset 2})))
