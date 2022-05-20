(ns tools.io-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [tools.io :as sut])
  (:import
   (java.util.zip GZIPInputStream)))

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
