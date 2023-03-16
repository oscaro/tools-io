(ns tools.io.core-test
  (:require
   [charred.api :as charred]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [are deftest is testing]]
   [tools.io :as tio]
   [tools.io.core :as sut])
  (:import (java.io File)))


(deftest strip-bom-test
  (testing "bom"
    (is (= "foo" (tio/strip-bom "\uFEFFfoo"))))
  (testing "no bom"
    (is (= "foo" (tio/strip-bom "foo")))
    (is (= "" (tio/strip-bom "")))))

(deftest join-path-test
  (is (= "foo/bar" (tio/join-path "foo/bar")))
  (is (= "foo/bar" (tio/join-path "foo" "bar")))
  (is (= "foo/bar" (tio/join-path "foo/" "bar")))
  (is (= "foo/bar" (tio/join-path "foo" "/bar")))
  (is (= "foo/bar" (tio/join-path "foo/" "/bar")))
  (is (= "foo/bar" (tio/join-path "foo//" "bar")))
  (is (= "/foo/bar" (tio/join-path "/foo/" "bar")))
  (is (= "gs://bar/a/b/c" (tio/join-path "gs://bar" "a" "b" "c")))

  (is (= "a/../b" (tio/join-path "a" ".." "b")))

  (is (thrown? AssertionError (tio/join-path nil)))
  (is (thrown? AssertionError (tio/join-path "gs://" "foo" nil "bar"))))

(deftest basename-test
  (are [expected fullpath] (= expected (tio/basename fullpath))
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
  (are [expected path] (= expected (tio/parent path))
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
  (are [expected fullpath] (= expected (tio/splitext fullpath))
    ["" ""]        ""
    ["/" ""]       "/"
    [".bashrc" ""] ".bashrc"
    ["foo" ""]     "foo"
    ["a.b/c" ""]   "a.b/c"

    ["foo" "tar"]    "foo.tar"
    ["foo.tar" "gz"] "foo.tar.gz"

    ["http://www.google.com/index" "html"] "http://www.google.com/index.html"))

(deftest expand-home-test
  (let [home (System/getProperty "user.home")]
    (are [expected path] (= expected (tio/expand-home path))
         ""   ""
         home "~"
         home home
         (str home "/foo/bar") "~/foo/bar")))

(deftest csv-test
  (testing "CSV file manipulation"
    (let [f (java.io.File/createTempFile "tool-io-tests" ".csv")
          data [["abc" "def"]
                ["ghi" "jkl"]]]
      (tio/write-csv-file f data)
      (is (= "abc,def\nghi,jkl\n" (slurp f))
          "data should be dumped in CSV format")
      (is (= data (tio/read-csv-file f))
          "same data should be read from CSV file")
      (.delete f))))

(deftest read-jsons-test
  (testing "reading resource json.gz file"
    (is (= 4 (count (tio/read-jsons-file (io/resource "good.jsons.gz"))))))
  (testing "reading resource json file with empty lines"
    (is (= 4 (count (tio/read-jsons-file (io/resource "good-with-empty-lines.jsons"))))))
  (testing "reading json file with empty lines"
    (is (= 4 (count (tio/read-jsons-file (str (io/resource "good-with-empty-lines.jsons")))))))
  (testing "reading json file with BOM"
    (is (= 1 (count (tio/read-jsons-file (str (io/resource "with-bom.jsons")))))))
  (testing "reading resource json files"
    (is (= 8 (count (tio/read-jsons-files [(io/resource "good-with-empty-lines.jsons")
                                           (io/resource "good.jsons.gz")]))))))


(defn- custom-edn-read [s]
  (edn/read-string {:readers {'double #(* % 2)}} s))

(deftest load-config-files-test
  (testing "reading resource test.edn file"
    (is (nil? (tio/load-config-file "not-exists.edn")))
    (is (thrown? AssertionError (tio/load-config-file "what-s-in-the-box?")) "unknown parser")
    (is (= 42 (:answer (tio/load-config-file (io/resource "test.edn")))) "load resource")
    (is (= 84 (:answer (tio/load-config-file (io/resource "test-with-reader-tag.edn") custom-edn-read))) "load resource with custom parser")
    (is (= 42 (:answer (tio/load-config-file "test.yml"))) "load filename as resource")
    (is (= 42 (:answer (tio/load-config-file "test/resources/test.json"))) "load absolute filename")
    (with-in-str "{:answer 42}"
     (is (= 42 (:answer (tio/load-config-file *in* edn/read-string))) "load from stdin (protocol independant)"))))

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

(deftest spit-slurp-test
  (let [text "Hey\n\nI \njust\n\n  met \n\tyou\n\n"
        filename ".spit-slurp-tmp-test-rw-text"]
    (io/delete-file filename true)
    (try
      (tio/spit filename text)
      (is (= text (tio/slurp filename)))
      (finally
        (io/delete-file filename true)))))

(deftest line-write-test
  (let [filename ".line-write-tmp-test-rw-text"]
    (io/delete-file filename true)
    (try
      (let [f (sut/file-writer filename)]
        (tio/line-write f "foo")
        (tio/line-write f "bar\n")
        (tio/line-write f "qux")
        (sut/close! f))
      (is (= ["foo" "bar" "qux"]
             (tio/read-text-file filename)))
      (finally
        (io/delete-file filename true)))))

(deftest lines-write-test
  (let [text ["We're" "no" "strangers" "to" "love"]
        filename ".lines-write-tmp-test-rw-text"]
    (io/delete-file filename true)
    (try
      (let [f (sut/file-writer filename)]
        (tio/lines-write f text))
      (is (= text
             (tio/read-text-file filename)))
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
      (is (= [obj]
             (tio/read-jsons-file *in*)))))))

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

(deftest list-dirs-test
  (testing "list dirs from existant dir"
    (tio/with-tempdir [dirname]
      (let [files (->> ["foo"
                        "bar/x"
                        "quux/x"
                        "baz"]
                       (map #(format "%s/%s" dirname %)))]
        (doseq [path files]
          (io/make-parents path)
          (spit path ""))
        (is (= 2 (count (tio/list-dirs dirname)))))))
  (testing "list dirs from existant without subfolders"
    (is (= 0 (count (tio/list-dirs "test/resources/test")))))
  (testing "list dirs from existant with trailing slash + subfolders"
    (tio/with-tempdir [dirname]
      (let [files (->> ["i"
                        "love/x/hh"
                        "testing/x/hey"
                        "clojure"
                        "code/itstrue"]
                       (map #(format "%s/%s/" dirname %)))]
        (doseq [path files]
          (io/make-parents path)
          (spit path ""))
        (is (= 3 (count (tio/list-dirs dirname)))))))
  (testing "list dirs from non existant directory"
    (is (= 0 (count (tio/list-dirs "i'm broken ~~~"))))))

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


(deftest zip-test
  (testing "zip correctly created"
    (let [archive "/tmp/test.zip"
          output "/tmp/extracted"]
      (is (= true (tio/zip-directory (io/resource "zip-mock")
                                     {:output-file archive
                                      :absolute? true})))
      (is (= true (tio/unzip-file archive {:output-folder output})))
      (is (= 3 (count (tio/list-files output)))))))
