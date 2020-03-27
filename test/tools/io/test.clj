(ns tools.io.test
  (:require [clojure.test :refer :all]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [tools.io :as tio]
            [tools.io.core :as cio]
            [clojure.edn :as edn])
  (:import [java.io File]))


(deftest strip-bom
  (testing "bom"
    (is (= "foo" (tio/strip-bom "\uFEFFfoo"))))
  (testing "no bom"
    (is (= "foo" (tio/strip-bom "foo")))
    (is (= "" (tio/strip-bom "")))))

(deftest join-path
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

(deftest basename
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

(deftest parent
  (are [expected path] (= expected (tio/parent path))
       nil ""
       nil "/"
       "/" "/foo"
       "/" "/foo/"
       "/foo" "/foo/bar"
       "foo" "foo/bar"
       "/a/b/c/d" "/a/b/c/d/foo.edns"
       "gs://foo/bar" "gs://foo/bar/qux"
       "gs://foo/bar" "gs://foo/bar/qux/"
       ))

(deftest splitext
  (are [expected fullpath] (= expected (tio/splitext fullpath))
    ["" ""]        ""
    ["/" ""]       "/"
    [".bashrc" ""] ".bashrc"
    ["foo" ""]     "foo"
    ["a.b/c" ""]   "a.b/c"

    ["foo" "tar"]    "foo.tar"
    ["foo.tar" "gz"] "foo.tar.gz"

    ["http://www.google.com/index" "html"] "http://www.google.com/index.html"))

(deftest expand-home
  (let [home (System/getProperty "user.home")]
    (are [expected path] (= expected (tio/expand-home path))
         ""   ""
         home "~"
         home home
         (str home "/foo/bar") "~/foo/bar")))

(deftest read-jsons
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

(deftest load-config-files
  (testing "reading resource test.edn file"
    (is (nil? (tio/load-config-file "not-exists")))
    (is (= 42 (:answer (tio/load-config-file (io/resource "test.edn")))) "load resource")
    (is (= 84 (:answer (tio/load-config-file (io/resource "test-with-reader-tag.edn") custom-edn-read))) "load resource with custom parser")
    (is (= 42 (:answer (tio/load-config-file "test.yml"))) "load filename as resource")
    (is (= 42 (:answer (tio/load-config-file "test-resources/test.json"))) "load absolute filename")))

(deftest read-txt
  (testing "reading resource test.txt file"
    (is (= "answer:42" (reduce str (tio/read-text-file (io/resource "test.txt")))))))

(deftest read-write-txt
  (let [text ["don't" "worry" "about" "the" "sardines."]
        filename ".tmp-test-rw-text"]
    (io/delete-file filename true)
    (try
      (tio/write-text-file filename text)
      (is (= text (tio/read-text-file filename)))
      (is (= (str/join "" (map #(str % "\n") text)) (tio/slurp filename)))
      (finally
        (io/delete-file filename true)))))

(deftest spit-slurp
  (let [text "Hey\n\nI \njust\n\n  met \n\tyou\n\n"
        filename ".tmp-test-rw-text"]
    (io/delete-file filename true)
    (try
      (tio/spit filename text)
      (is (= text (tio/slurp filename)))
      (finally
        (io/delete-file filename true)))))

(deftest line-write
  (let [filename ".tmp-test-rw-text"]
    (io/delete-file filename true)
    (try
      (let [f (cio/file-writer filename)]
        (tio/line-write f "foo")
        (tio/line-write f "bar\n")
        (tio/line-write f "qux")
        (cio/close! f))
      (is (= ["foo" "bar" "qux"]
             (tio/read-text-file filename)))
      (finally
        (io/delete-file filename true)))))

(deftest lines-write
  (let [text ["We're" "no" "strangers" "to" "love"]
        filename ".tmp-test-rw-text"]
    (io/delete-file filename true)
    (try
      (let [f (cio/file-writer filename)]
        (tio/lines-write f text))
      (is (= text
             (tio/read-text-file filename)))
      (finally
        (io/delete-file filename true)))))

(deftest get-default-file-types
  (are [ftype filename] (= ftype (cio/get-file-type filename))
       :base "file://foo"
       :base "/foo"
       :base "../foo"
       :base "bar"
       :base "file://toto"
       :base "file:///toto"

       :http "http://www.google.com"
       :http "https://www.google.com"

       :stdin *in*))

(deftest custom-file-type
  (let [file-type :test1234
        filename "test1234://foobar"]
    (is (thrown? Exception (cio/get-file-type filename)))

    (try
       (cio/register-file-pred!
         file-type #(str/starts-with? (str %) "test1234://"))

       (is (= file-type (cio/get-file-type filename)))

       (finally
          (cio/unregister-file-pred! file-type)))))

(deftest read-from-stdin
  (with-in-str "{\"toto\": 42}"
    (is (= [{:toto 42}]
           (tio/read-jsons-file *in*))))

  (testing "long line"
    (let [obj (vec (range 9000))
          s (json/generate-string obj)]
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


(deftest list-files
  (testing "list files from an existant dir"
    (is (= 5 (count (tio/list-files "test-resources/test")))))
  (testing "list files from an existant file"
    (is (= "test-resources/test.txt"
            (first (tio/list-files "test-resources/test.txt")))))
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
          (= [(format "%s/aaa/x" dirname)]
             (vec (tio/list-files (tio/join-path dirname "aaa/")))))

        (testing "trailing slash no dir"
          (empty? (tio/list-files (tio/join-path dirname "foo/")))
          (empty? (tio/list-files (tio/join-path dirname "aaa/x/"))))))))


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

(deftest exists?
  (testing "exists? with existant dir"
    (is (tio/exists? "test-resources/")))
  (testing "exists? with existant file"
    (is (tio/exists? "test-resources/test.txt")))
  (testing "exists? with an inexistant file"
    (is (not (tio/exists? "-i do no exists-")))))
