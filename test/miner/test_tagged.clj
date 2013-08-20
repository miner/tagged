(ns miner.test-tagged
  (:require [miner.tagged :as tag]
            [clojure.test :refer :all]))

(defrecord Foo [a] tag/EdnRecord)

(deftest factory []
  (is (= (tag/class->factory miner.test_tagged.Foo) #'map->Foo))
  (is (= ((tag/class->factory miner.test_tagged.Foo) {:a 1}) (map->Foo {:a 1})))
  (is (= (tag/tag->factory 'miner.test-tagged/Foo) #'map->Foo))
  (is (= ((tag/tag->factory 'miner.test-tagged/Foo) {:a 11}) (map->Foo {:a 11}))))
  

(deftest reading-and-printing []
  (let [unknown-string "#unk.ns/Unk 42"
        foo-string "#miner.test-tagged/Foo 42"
        unk42 (tag/->TaggedValue 'unk.ns/Unk 42)
        foo42 (->Foo 42)
        nested-string "#miner.test-tagged/Foo {:a #unk.ns/Unk 42}"
        nested (->Foo unk42)]
    (is (tag/read-string unknown-string) unk42)
    (is (tag/read-string foo-string) foo42)
    (is (pr-str foo42) foo-string)
    (is (pr-str unk42) unknown-string)
    (is (pr-str (tag/read-string unknown-string)) unknown-string)
    (is (tag/read-string (pr-str (tag/read-string unknown-string))) unk42)
    (is (pr-str (tag/read-string foo-string)) foo42)
    (is (tag/read-string (pr-str (tag/read-string foo-string))) foo42)
    (is (pr-str (tag/read-string nested-string)) nested)
    (is (tag/read-string (pr-str (tag/read-string (pr-str nested)))) nested)))
