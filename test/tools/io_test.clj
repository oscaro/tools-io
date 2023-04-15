(ns tools.io-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [are deftest is testing]]
   [tools.io :as sut]
   [tools.io.core :refer [file-writer]])
  (:import
   (java.util.zip GZIPInputStream GZIPOutputStream)
   (org.apache.commons.compress.archivers.zip ZipArchiveEntry ZipFile)))

(deftest strip-bom-test
  (testing "bom"
    (is (= "foo" (sut/strip-bom "\uFEFFfoo"))))
  (testing "no bom"
    (is (= "foo" (sut/strip-bom "foo")))
    (is (= "" (sut/strip-bom "")))))

(deftest join-path-test
  (is (= "foo/bar" (sut/join-path "foo/bar")))
  (is (= "foo/bar" (sut/join-path "foo" "bar")))
  (is (= "foo/bar" (sut/join-path "foo/" "bar")))
  (is (= "foo/bar" (sut/join-path "foo" "/bar")))
  (is (= "foo/bar" (sut/join-path "foo/" "/bar")))
  (is (= "foo/bar" (sut/join-path "foo//" "bar")))
  (is (= "/foo/bar" (sut/join-path "/foo/" "bar")))
  (is (= "gs://bar/a/b/c" (sut/join-path "gs://bar" "a" "b" "c")))

  (is (= "a/../b" (sut/join-path "a" ".." "b")))

  (is (thrown? AssertionError (sut/join-path nil)))
  (is (thrown? AssertionError (sut/join-path "gs://" "foo" nil "bar"))))

(deftest basename-test
  (are [expected fullpath] (= expected (sut/basename fullpath))
    ""    "/"
    ""    "///////"
    "foo" "foo"
    "foo" "foo/"
    "bar" "foo/bar"
    "bar" "foo/bar/"
    "yo"  "http://www.example.com/yo"
    "ya"  "gs://foo-bar-uqx/1/2/ya"
    "hey.gz" "/hey.gz"))

(deftest parent-test
  (are [expected path] (= expected (sut/parent path))
       nil ""
       nil "/"
       "/" "/foo"
       "/" "/foo/"
       "/foo" "/foo/bar"
       "foo" "foo/bar"
       "/a/b/c/d" "/a/b/c/d/foo.edns"
       "gs://foo/bar" "gs://foo/bar/qux"
       "gs://foo/bar" "gs://foo/bar/qux/"))

(deftest splitext-test
  (are [expected fullpath] (= expected (sut/splitext fullpath))
    ["" ""]        ""
    ["/" ""]       "/"
    [".bashrc" ""] ".bashrc"
    ["foo" ""]     "foo"
    ["a.b/c" ""]   "a.b/c"

    ["foo" "tar"]    "foo.tar"
    ["foo.tar" "gz"] "foo.tar.gz"

    ["http://www.google.com/index" "html"] "http://www.google.com/index.html"))

(deftest lines-write-test
  (let [text ["We're" "no" "strangers" "to" "love"]
        filename ".lines-write-tmp-test-rw-text"]
    (io/delete-file filename true)
    (try
      (let [f (file-writer filename)]
        (sut/lines-write f text))
      (is (= text
             (sut/read-text-file filename)))
      (finally
        (io/delete-file filename true)))))

(deftest list-dirs-test
  (testing "list dirs from existant dir"
    (sut/with-tempdir [dirname]
      (let [files (->> ["foo"
                        "bar/x"
                        "quux/x"
                        "baz"]
                       (map #(format "%s/%s" dirname %)))]
        (doseq [path files]
          (io/make-parents path)
          (spit path ""))
        (is (= 2 (count (sut/list-dirs dirname)))))))

  (testing "list dirs from existant without subfolders"
    (is (= 0 (count (sut/list-dirs "test/resources/test")))))

  (testing "list dirs from existant with trailing slash + subfolders"
    (sut/with-tempdir [dirname]
      (let [files (->> ["i"
                        "love/x/hh"
                        "testing/x/hey"
                        "clojure"
                        "code/itstrue"]
                       (map #(format "%s/%s/" dirname %)))]
        (doseq [path files]
          (io/make-parents path)
          (spit path ""))
        (is (= 3 (count (sut/list-dirs dirname)))))))

  (testing "list dirs from non existant directory"
    (is (= 0 (count (sut/list-dirs "i'm broken ~~~"))))))

(deftest spit-test
  (let [text "Hey\n\nI \njust\n\n  met \n\tyou\n\n"
        filename ".spit-slurp-tmp-test-rw-text"]
    (io/delete-file filename true)
    (try
      (sut/spit filename text)
      (is (= text (slurp filename)))
      (finally
        (io/delete-file filename true)))))

(defn- write-fixture
  [path data]
  (with-open [w (io/writer (cond->> (io/output-stream path)
                             (str/ends-with? path ".gz")
                             GZIPOutputStream.))]
    (.write w ^String data)))

(deftest slurp-test
  (let [test-file "test.txt"]
    (is (= (slurp (io/resource test-file))
           (sut/slurp (io/resource test-file))))))

(deftest read-jsons-file-test
  (testing "with resources"
    (testing "reading resource json.gz file"
      (is (= 4 (count (sut/read-jsons-file (io/resource "good.jsons.gz"))))))
    (testing "reading resource json file with empty lines"
      (is (= 4 (count (sut/read-jsons-file (io/resource "good-with-empty-lines.jsons"))))))
    (testing "reading json file with empty lines"
      (is (= 4 (count (sut/read-jsons-file (str (io/resource "good-with-empty-lines.jsons")))))))
    (testing "reading json file with BOM"
      (is (= 1 (count (sut/read-jsons-file (str (io/resource "with-bom.jsons")))))))
    (testing "reading resource json files"
      (is (= 8 (count (sut/read-jsons-files [(io/resource "good-with-empty-lines.jsons")
                                             (io/resource "good.jsons.gz")]))))))

  (let [expected [{:a 1 :b 2.3 :c "c1/to" :d [1 2 3] :e [4 2 45]}
                  {:a 2 :b 12.3 :c "c2/\"ta" :d [45 5] :f [1]}]
        data "{\"a\":1,\"b\":2.3,\"c\":\"c1\\/to\",\"d\":[1,2,3],\"e\":[4,2,45]}\n{\"a\":2,\"b\":12.3,\"c\":\"c2\\/\\\"ta\",\"d\":[45,5],\"f\":[1]}\n"]

    (sut/with-tempdir [tmp-dir]
      (testing "nominal case"
        (testing "with collection"
          (let [file-path (sut/join-path tmp-dir "test-coll.jsons")]
            (write-fixture file-path data)

            (let [rslt (sut/read-jsons-file file-path)]
              (is (= expected
                     rslt)))))

        (testing "idents"
          (let [data "1\n2\n3"
                file-path (sut/join-path tmp-dir "test-ident.jsons")]
            (write-fixture file-path data)
            (let [rslt (sut/read-jsons-file file-path)]
              (is (= [1 2 3]
                     rslt))))))

      (testing "with compression"
        (let [file-path (sut/join-path tmp-dir "test-comp.jsons.gz")]
          (write-fixture file-path data)

          (let [rslt (sut/read-jsons-file file-path)]
            (is (= expected
                   rslt))))))))

(deftest write-jsons-file-test
  (let [data [{:a 1 :b 2.3 :c "c1/to" :d [1 2 3] :e #{4 2 45}}
              {:a 2 :b 12.3 :c "c2/\"ta" :d [45 5] :f #{1}}]
        expected "{\"a\":1,\"b\":2.3,\"c\":\"c1/to\",\"d\":[1,2,3],\"e\":[4,2,45]}\n{\"a\":2,\"b\":12.3,\"c\":\"c2/\\\"ta\",\"d\":[45,5],\"f\":[1]}\n"]

    (sut/with-tempdir [tmp-dir]
      (testing "nominal case"
        (let [file-path (sut/join-path tmp-dir "test.jsons")]
          (sut/write-jsons-file file-path data)

          (let [rslt (slurp file-path)]
            (is (= expected
                   rslt)))))

      (testing "with compression"
        (let [file-path (sut/join-path tmp-dir "test.jsons.gz")]
          (sut/write-jsons-file file-path data)

          (let [rslt (->> file-path
                          (io/input-stream)
                          (GZIPInputStream.)
                          slurp)]
            (is (= expected
                   rslt))))))))

(deftest read-edns-file-test
  (let [expected [{:a 1 :b 2.3 :c "c1/to" :d [1 2 3] :e [4 2 45]}
                  {:a 2 :b 12.3 :c "c2/\"ta" :d [45 5] :f [1]}]
        data "{:a 1, :b 2.3, :c \"c1/to\", :d [1,2,3], :e [4,2,45]}\n{:a 2, :b 12.3, :c \"c2/\\\"ta\", :d [45,5], :f [1]}\n"]

    (sut/with-tempdir [tmp-dir]
      (testing "nominal case"
        (testing "with collection"
          (let [file-path (sut/join-path tmp-dir "test-coll.edns")]
            (write-fixture file-path data)

            (let [rslt (sut/read-edns-file file-path)]
              (is (= expected
                     rslt))))))

      (testing "with compression"
          (let [file-path (sut/join-path tmp-dir "test-comp.edns.gz")]
            (write-fixture file-path data)

            (let [rslt (sut/read-edns-file file-path)]
              (is (= expected
                     rslt))))))))

(deftest write-edn-file-test
  (let [data [[1 2 3 4 "abc" 434.23]
              [10 20 30 40 "edf" 1432.23123]]
        expected "[[1 2 3 4 \"abc\" 434.23] [10 20 30 40 \"edf\" 1432.23123]]\n"]

    (sut/with-tempdir [tmp-dir]
      (testing "nominal case"
        (let [file-path (sut/join-path tmp-dir "test.edn")]
          (sut/write-edn-file file-path data)

          (let [rslt (slurp file-path)]
            (is (= expected
                   rslt)))))

      (testing "with compression"
        (let [file-path (sut/join-path tmp-dir "test.csvs.gz")]
          (sut/write-edn-file file-path data)

          (let [rslt (->> file-path
                          (io/input-stream)
                          (GZIPInputStream.)
                          slurp)]
            (is (= expected
                   rslt))))))))

(deftest read-csv-file-test
  (testing "CSV file manipulation"
    (let [f (java.io.File/createTempFile "tool-io-tests" ".csv")
          data [["abc" "def"]
                ["ghi" "jkl"]]]
      (sut/write-csv-file f data)
      (is (= "abc,def\nghi,jkl\n" (slurp f))
          "data should be dumped in CSV format")
      (is (= data (sut/read-csv-file f))
          "same data should be read from CSV file")
      (.delete f)))

  (let [expected [["1" "2" "3" "4" "abc" "434.23"] ["10" "20" "30" "40" "edf" "1432.23123"]]
        data "1,2,3,4,\"abc\",434.23\n10,20,30,40,\"edf\",1432.23123\n"]
    (sut/with-tempdir [tmp-dir]
      (testing "nominal case"
        (let [file-path (sut/join-path tmp-dir "test.csvs")]
          (write-fixture file-path data)

          (let [rslt (sut/read-csv-file file-path)]
            (is (= expected
                   rslt)))))

      (testing "with compression"
        (let [file-path (sut/join-path tmp-dir "test.csvss.gz")]
          (write-fixture file-path data)

          (let [rslt (sut/read-csv-file file-path)]
            (is (= expected
                   rslt))))))))


(deftest write-csv-file-test
  (let [data [[1 2 3 4 "abc" 434.23]
              [10 20 30 40 "edf" 1432.23123]]
        expected "1,2,3,4,abc,434.23\n10,20,30,40,edf,1432.23123\n"]

    (sut/with-tempdir [tmp-dir]
      (testing "nominal case"
        (let [file-path (sut/join-path tmp-dir "test.csvs")]
          (sut/write-csv-file file-path data)

          (let [rslt (slurp file-path)]
            (is (= expected
                   rslt)))))

      (testing "with compression"
        (let [file-path (sut/join-path tmp-dir "test.csvs.gz")]
          (sut/write-csv-file file-path data)

          (let [rslt (->> file-path
                          (io/input-stream)
                          (GZIPInputStream.)
                          slurp)]
            (is (= expected
                   rslt))))))))

(defn- custom-edn-read [s]
  (edn/read-string {:readers {'double #(* % 2)}} s))

(deftest load-config-files-test
  (testing "reading resource test.edn file"
    (is (nil? (sut/load-config-file "not-exists.edn")))
    (is (thrown? AssertionError (sut/load-config-file "what-s-in-the-box?"))
        "unknown parser")
    (is (= 42 (:answer (sut/load-config-file (io/resource "test.edn"))))
        "load resource")
    (is (= 84 (:answer (sut/load-config-file (io/resource "test-with-reader-tag.edn") custom-edn-read)))
        "load resource with custom parser")
    (is (= 42 (:answer (sut/load-config-file "test.yml")))
        "load filename as resource")
    (is (= 42 (:answer (sut/load-config-file "test/resources/test.json")))
        "load absolute filename")

    (with-in-str "{:answer 42}"
     (is (= 42 (:answer (sut/load-config-file *in* edn/read-string)))
         "load from stdin (protocol independant)"))))

(defn- zip-contents
  [path]
  (with-open [z (ZipFile. (io/file path))]
    (for [^ZipArchiveEntry entry (enumeration-seq (.getEntries z))]
      (.getName entry))))

(deftest zip-directory-test
  (testing "zip correctly created"
    (sut/with-tempdir [tmp-dir]
      (let [archive (sut/join-path tmp-dir "test.zip")]
        (is (true?
             (sut/zip-directory (io/resource "zip-mock")
                                {:output-file archive
                                 :absolute? true})))
        (let [contents (->> (zip-contents archive)
                            (map sut/basename)
                            set)]
          (is (= #{"a.edn" "b.edn" "c.edn"}
                 contents)))))))

(deftest unzip-file-test
  (sut/with-tempdir [tmp-dir]
    (let [output (sut/join-path tmp-dir "extracted")]
      (is (true? (sut/unzip-file "test/resources/zip-mock.zip"
                                 {:output-folder output})))
      (let [files (sut/list-files output)
            basenames (->> files
                           (map sut/basename)
                           set)]
        (is (= #{"a.edn" "b.edn" "c.edn"}
               basenames))
        (is (every? #(= "{:hey true}\n" (slurp %)) files))))))
