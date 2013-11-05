(ns miner.tagged
  (:refer-clojure :exclude [read read-string])
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

;; adapted from "The Data-Reader's Guide to the Galaxy" talk at Clojure/West 2013

;; Holder for unknown tags
(defrecord TaggedValue [tag value]
  Object 
  (toString [x] (pr-str x)))

(defn tag->factory
  "Returns the map-style record factory for the `tag` symbol.  Returns nil if `tag` does not
  refer to a record."
  [tag]
  (when (namespace tag)
    (resolve (symbol (str (namespace tag) "/map->" (name tag))))))

(defn tagged-default-reader 
  "Default data-reader for reading an EDN tagged literal as a Record.  If the tag corresponds to a
  known Record class (tag my.ns/Rec for class my.ns.Rec), use that Record's map-style factory on
  the given map value.  If the tag is unknown, use the generic miner.tagged.TaggedValue."  
  [tag value]
  (if-let [factory (and (map? value)
                        (Character/isUpperCase ^Character (first (name tag)))
                        (tag->factory tag))]
    (factory value)
    (->TaggedValue tag value)))

(defn- record-name [record-class]
  "Returns the record's name as a String given the class `record-class`."
  (str/replace (pr-str record-class) \_ \-))

(defn- tag-string
  "Returns the string representation of the tag corresponding to the given `record-class`."
  [record-class]
  (let [cname (record-name record-class)
        dot (.lastIndexOf ^String cname ".")]
    (when (pos? dot)
      (str (subs cname 0 dot) "/" (subs cname (inc dot))))))

(defn class->tag
  "Returns the tag symbol for the given `record-class`."
  [record-class]
  (when-let [tagstr (tag-string record-class)]
    (symbol tagstr)))

(defn class->factory
  "Returns the map-style record factory for the `record-class`."
  [record-class]
  (let [cname (record-name record-class)
        dot (.lastIndexOf ^String cname ".")]
    (when (pos? dot)
      (resolve (symbol (str (subs cname 0 dot) "/map->" (subs cname (inc dot))))))))

;; preserve the original string representation of the unknown tagged literal
(defmethod print-method miner.tagged.TaggedValue [this ^java.io.Writer w]
   (.write w "#")
   (print-method (:tag this) w)
   (.write w " ")
   (print-method (:value this) w))

(defn pr-tagged-record-on
  "Prints the EDN tagged literal representation of the record `this` on the java.io.Writer `w`.
  Useful for implementing a print-method on a record class.  For example:

     (defmethod print-method my.ns.MyRecord [this w]
       (miner.tagged/pr-tagged-record-on this w))"
  [this ^java.io.Writer w]
  (.write w "#")
  (.write w ^String (tag-string (class this)))
  (.write w " ")
  (print-method (into {} this) w))

(def default-tagged-read-options {:default #'tagged-default-reader})
;; other possible keys :eof and :readers

(defn read
  "Like clojure.edn/read but the :default option is `tagged-default-reader`."
  ([] (edn/read default-tagged-read-options *in*))
  ([stream] (edn/read default-tagged-read-options stream))
  ([options stream] (edn/read (merge default-tagged-read-options options) stream)))

(defn read-string 
  "Like clojure.edn/read-string but the :default option is `tagged-default-reader`."
  ([s] (edn/read-string default-tagged-read-options s))
  ([options s] (edn/read-string (merge default-tagged-read-options options) s)))


(defprotocol EdnTag
  (edn-tag [this])
  (edn-str [this])
  (edn-value [this]))

(extend-protocol EdnTag
  nil
  (edn-tag [this] nil)
  (edn-str [this] (pr-str this))
  (edn-value [this] this)

  Object
  (edn-tag [this] nil)
  (edn-str [this] (pr-str this))
  (edn-value [this] this)

  miner.tagged.TaggedValue
  (edn-tag [this] (:tag this))
  (edn-str [this] (pr-str this))
  (edn-value [this] (:value this))

  clojure.lang.IRecord
  (edn-tag [this] (class->tag (class this)))
  (edn-str [this] (with-out-str (pr-tagged-record-on this *out*)))
  (edn-value [this] (into {} this))

  java.util.Date 
  (edn-tag [this] 'inst)
  (edn-str [this] (pr-str this))
  (edn-value [this] this)

  java.util.Calendar
  (edn-tag [this] 'inst)
  (edn-str [this] (pr-str this))
  (edn-value [this] this)

  java.sql.Timestamp
  (edn-tag [this] 'inst)
  (edn-str [this] (pr-str this))
  (edn-value [this] this)

  java.util.UUID
  (edn-tag [this] 'uuid)
  (edn-str [this] (pr-str this))  
  (edn-value [this] this)  )

;; In the REPL, you can use this to install the tagged-default-reader as the default:

(comment 

  (require '[miner.tagged :as tag])
  (alter-var-root #'clojure.core/*default-data-reader-fn* (constantly tag/tagged-default-reader))

)

;; However, it's probably better just to use the `miner.tagged/read` and `miner.tagged/read-string`
;; functions.
