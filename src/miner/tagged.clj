(ns miner.tagged
  (:refer-clojure :exclude [read read-string])
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

;; adapted from "The Data-Reader's Guide to the Galaxy" talk at Clojure/West 2013

(declare edn-str)

;; Holder for unknown tags
(defrecord TaggedValue [tag value]
  Object 
  (toString [x] (edn-str x)))

(defn tag->factory
  "Returns the map-style record factory for the `tag` symbol.  Returns nil if the `tag` is
  unqualified."  
  [tag]
  (when (namespace tag)
    (resolve (symbol (str (namespace tag) "/map->" (name tag))))))

(defn tagged-default-reader 
  "Default data-reader for reading an EDN tagged literal as a Record.  If the tag corresponds to a
  known Record class (tag my.ns/Rec for class my.ns.Rec), use that Record's map-style factory on
  the given map value.  If the tag is unknown, use the generic miner.tagged.TaggedValue."  
  [tag value]
  (if-let [factory (and (map? value)
                            (Character/isUpperCase (first (name tag)))
                            (tag->factory tag))]
    (factory value)
    (->TaggedValue tag value)))

(defprotocol Ednable
  (as-edn-str [x]))

(defn record-name [rec-class]
  (str/replace (pr-str rec-class) \_ \-))

(defn tag-str [record-class]
  "Returns the string representation of the tag corresponding to the given `record-class`."
  (let [cname (record-name record-class)
        dot (.lastIndexOf cname ".")]
    (when (pos? dot)
      (str (subs cname 0 dot) "/" (subs cname (inc dot))))))

(defn class->factory [record-class]
  "Returns the map-style record factory for the `record-class`."
  (let [cname (record-name record-class)
        dot (.lastIndexOf cname ".")]
    (when (pos? dot)
      (resolve (symbol (str (subs cname 0 dot) "/map->" (subs cname (inc dot))))))))


(extend-protocol Ednable
  nil
  (as-edn-str [x] (pr-str x))

  Object
  (as-edn-str [x] (pr-str x))

  ;; represent records as tagged literals
  clojure.lang.IRecord
  (as-edn-str [x] (str "#" (tag-str (class x)) " " (pr-str (into {} x))))

  ;; preserve the original string representation of the unknown tagged literal
  miner.tagged.TaggedValue
  (as-edn-str [x] (str "#" (pr-str (:tag x)) " " (pr-str (:value x))))

)

(defn edn-str
  "Similar to pr-str, but represents Records as EDN tagged literals."
  ([] "")
  ([x] (as-edn-str x))
  ([x & more]
     (str/join " " (map as-edn-str (conj more x)))))

(def default-tagged-read-options {:default #'tagged-default-reader})
;; other possible keys :eof and :readers

(defn read
  "Like clojure.edn/read but the :default option is `tagged-default-reader`."
  ([] (read *in*))
  ([stream] (read {} stream))
  ([options stream] (edn/read (merge default-tagged-read-options options) stream)))

(defn read-string 
  "Like clojure.edn/read-string but the :default option is `tagged-default-reader`."
  ([s] (read-string {} s))
  ([options s] (edn/read-string (merge default-tagged-read-options options) s)))


;; In the REPL, you can use this to install the default-reader as the default:

(comment 
  (require '[miner.tagged :as tag])
  (alter-var-root #'clojure.core/*default-data-reader-fn* (constantly tag/tagged-default-reader))
)

;; However, it's better just to use the provided `miner.tagged/read` and `miner.tagged/read-string` 
;; functions.

;; You could use edn-str in a custom print-method for your record type if you always want to use the
;; tagged literal notation.

(comment 
 (require '[miner.tagged :as tag])
 (defmethod print-method my.ns.Rec [^my.ns.Rec this ^java.io.Writer w] 
   (.write w (tag/edn-str this)))
)
