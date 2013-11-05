(ns miner.test-tagged
  (:require [miner.tagged :as tag]
            [clojure.test :refer :all]))

(defrecord Foo [a])

(defmethod print-method miner.test_tagged.Foo [this w]
  (tag/pr-tagged-record-on this w))

(deftest factory
  (is (= (tag/class->factory miner.test_tagged.Foo) #'map->Foo))
  (is (= ((tag/class->factory miner.test_tagged.Foo) {:a 1}) (map->Foo {:a 1})))
  (is (= (tag/tag->factory 'miner.test-tagged/Foo) #'map->Foo))
  (is (= ((tag/tag->factory 'miner.test-tagged/Foo) {:a 11}) (map->Foo {:a 11}))))
  
(deftest tag-symbol 
  (is (= (tag/class->tag miner.test_tagged.Foo) 'miner.test-tagged/Foo))
  (is (= (tag/class->tag (class (->Foo 42))) 'miner.test-tagged/Foo)))

(deftest edn-tag-and-str
  (is (nil? (tag/edn-tag 42)))
  (is (= (tag/edn-str 'foo) "foo"))
  (is (= (tag/edn-tag (->Foo 42)) 'miner.test-tagged/Foo))
  (is (= (tag/edn-str (->Foo 42)) "#miner.test-tagged/Foo {:a 42}"))
  (is (= (tag/edn-tag (java.util.Date.)) 'inst))
  (is (= (tag/edn-tag (java.util.Calendar/getInstance)) 'inst))
  (is (= (tag/edn-tag (java.sql.Timestamp. 0)) 'inst))
  (is (= (tag/edn-str (java.sql.Timestamp. 0)) "#inst \"1970-01-01T00:00:00.000000000-00:00\""))
  (is (= (tag/edn-tag (java.util.UUID/fromString "277826bd-e220-4809-806e-ef906d8fb6b4")) 'uuid))
  (is (= (tag/edn-str (java.util.UUID/fromString "277826bd-e220-4809-806e-ef906d8fb6b4"))
         "#uuid \"277826bd-e220-4809-806e-ef906d8fb6b4\"")))

(deftest edn-values
  (is (nil? (tag/edn-value nil)))
  (is (= (tag/edn-value (java.util.Date. 0)) (java.util.Date. 0)))
  (is (= (tag/edn-value 'foo) 'foo))
  (is (= (tag/edn-value (->Foo 42)) {:a 42})))  

(deftest reading-and-printing
  (let [unknown-string "#unk.ns/Unk 42"
        foo-string "#miner.test-tagged/Foo {:a 42}"
        unk42 (tag/->TaggedValue 'unk.ns/Unk 42)
        foo42 (->Foo 42)
        nested-string "#miner.test-tagged/Foo {:a #unk.ns/Unk 42}"
        nested (->Foo unk42)]
    (is (= (tag/read-string unknown-string) unk42))
    (is (= (tag/edn-tag (tag/read-string unknown-string)) 'unk.ns/Unk))
    (is (= (tag/edn-value (tag/read-string unknown-string)) 42))
    (is (= (tag/read-string foo-string) foo42))
    (is (= (pr-str foo42) foo-string))
    (is (= (pr-str unk42) unknown-string))
    (is (= (pr-str (tag/read-string unknown-string)) unknown-string))
    (is (= (tag/read-string (pr-str (tag/read-string unknown-string))) unk42))
    (is (= (pr-str (tag/read-string foo-string)) (pr-str foo42)))
    (is (= (tag/read-string (pr-str (tag/read-string foo-string))) foo42))
    (is (= (pr-str (tag/read-string nested-string)) (pr-str nested)))
    (is (= (tag/read-string (pr-str (tag/read-string (pr-str nested)))) nested))))
