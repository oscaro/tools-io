(ns tools.io-test
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [tools.io :as sut])
  (:import
   (java.util.zip GZIPInputStream GZIPOutputStream)))

(defn- write-fixture
  [path data]
  (with-open [w (io/writer (cond->> (io/output-stream path)
                             (str/ends-with? path ".gz")
                             GZIPOutputStream.))]
    (.write w ^String data)))

(deftest read-jsons-file-test
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
        expected "[[[1 2 3 4 \"abc\" 434.23] [10 20 30 40 \"edf\" 1432.23123]]]\n"]

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
