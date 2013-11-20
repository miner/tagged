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

;; We would like to be able to compose multiple functions to implement a data-reader.  The
;; idea is that one data-reader could call a sequence of "tag-reader" fns until one returns
;; a truthy value, which is the result for the data-reader.  A tag-reader fn takes two args
;; the tag and val (like *default-data-reader-fn*).  A tag-reader returns the appropriate
;; result, or nil to decline.  That makes it simpler to compose multiple tag-readers.
;; However, remember that you should not return nil as the final result of a data-reader
;; (see CLJ-1138) so your data-reader should throw a useful exception.  (Note that `false`
;; is non-truthy so it's treated like nil.)  Use `some-tag-reader-fn` to combine tag-reader
;; fns with a default exception if no tag-reader succeeds.

(defn throw-tag-reader [tag val]
  (throw (ex-info (str "No appropriate reader function for tag " tag)
                  {:tag tag :value val})))

(defn record-tag-reader [tag val]
  (when-let [factory (and (map? val)
                          (Character/isUpperCase ^Character (first (name tag)))
                          (tag->factory tag))]
    (factory val)))

(defn- keep-first [f xs]
  "Returns first truthy result of lazily applying `f` to each of the elements of `xs`.
  Returns nil if no truthy result is found.  Unlike `keep`, will not return false."
  (first (remove false? (keep f xs))))

(defn some-tag-reader-fn
  "Takes any number of tag-reader functions and returns a default data-reader fn taking two
  args, tag and value.  The data-reader will either return a truthy value or throw an exception
  if the tag cannot be handled appropriately with the value."
  ([] throw-tag-reader)
  ([f] (fn [tag val] (or (f tag val) (throw-tag-reader tag val))))
  ([f g] (fn [tag val] (or (f tag val) (g tag val) (throw-tag-reader tag val))))
  ([f g h] (fn [tag val] (or (f tag val) (g tag val) (h tag val) (throw-tag-reader tag val))))
  ([f g h & more] (fn [tag val]
                    (or (keep-first (fn [r] (r tag val)) (conj more h g f))
                        (throw-tag-reader tag val)))))

(def tagged-default-reader 
  "Default data-reader for reading an EDN tagged literal as a Record.  If the tag corresponds to a
  known Record class (tag my.ns/Rec for class my.ns.Rec), use that Record's map-style factory on
  the given map value.  If the tag is unknown, use the generic miner.tagged.TaggedValue."  
  (some-tag-reader-fn record-tag-reader ->TaggedValue))

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
