(ns miner.test-tagged
  (:require [miner.tagged :as tag]
            [clojure.test :refer :all]))

(defrecord Foo [a])

(deftest factory []
  (is (= (tag/class->factory miner.test_tagged.Foo) #'map->Foo))
  (is (= ((tag/class->factory miner.test_tagged.Foo) {:a 1}) (map->Foo {:a 1})))
  (is (= (tag/tag->factory 'miner.test-tagged/Foo) #'map->Foo))
  (is (= ((tag/tag->factory 'miner.test-tagged/Foo) {:a 11}) (map->Foo {:a 11}))))
  

(deftest basics []
  ;; for basic things edn-str is like pr-str
  (doseq [x [:a 'foo [[[:deep]]] {:a 1} "basics" 42 (range 10)]]
    (is (tag/edn-str x) (pr-str x))))

(deftest reading-and-printing []
  (let [unknown-string "#unk.ns/Unk 42"
        foo-string "#miner.test-tagged/Foo 42"
        unk42 (tag/->TaggedValue 'unk.ns/Unk 42)
        foo42 (->Foo 42)]
    (is (tag/read-string unknown-string) unk42)
    (is (tag/read-string foo-string) foo42)
    (is (tag/edn-str foo42) foo-string)
    (is (tag/edn-str unk42) unknown-string)
    (is (tag/edn-str (tag/read-string unknown-string)) unknown-string)
    (is (tag/read-string (tag/edn-str (tag/read-string unknown-string))) unk42)
    (is (tag/edn-str (tag/read-string foo-string)) foo42)
    (is (tag/read-string (tag/edn-str (tag/read-string foo-string))) foo42)))



  
