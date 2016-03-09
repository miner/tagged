(ns miner.tagged
  (:refer-clojure :exclude [read read-string])
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

;; adapted from "The Data-Reader's Guide to the Galaxy" talk at Clojure/West 2013

;; Holder for unknown tags
(defrecord TaggedValue [tag value]
  Object 
  (toString [x] (pr-str x)))

(defn- calc-tag->factory
  "Resolves the map-style record factory for the `tag` symbol.  Returns nil if `tag` does not
  refer to a record."
  [tag]
  (when (namespace tag)
    (resolve (symbol (str (namespace tag) "/map->" (name tag))))))

(def ^{:arglists (:arglists (meta #'calc-tag->factory))} tag->factory
  "Returns the map-style record factory for the `tag` symbol.  Returns nil if `tag` does not
  refer to a record.  Results are memoized."
  (memoize calc-tag->factory))


;; A "tag-reader" is a fn taking two args, the tag symbol and a value, like a
;; *default-data-reader-fn*.  Unlike a data-reader, a tag-reader may return nil if it does not
;; want to handle a particular value.  (See CLJ-1138 for more information about why a
;; data-reader is not allowed to return nil.)  The tag-reader convention makes is simpler to
;; compose multiple reader functions as a single data-reader.  You can combine several
;; tag-readers with `some-tag-reader`.  You can wrap a tag-reader to work as a data-reader
;; with `data-reader` which throws if none of the tag-readers returns non-nil.  Note that a
;; data-reader must return a truthy value or throw an exception.  The `throw-tag-reader`
;; always throws so it's appropriate to use as your last resort tag-reader.

(defn throw-tag-reader
  "Always throws an exception for a `tag` and `val`."
  [tag val]
  (throw (ex-info (str "No appropriate tag-reader function for tag " tag)
                  {:tag tag :value val})))

(defn record-tag-reader
  "If the tag corresponds to a record class (for example, tag my.ns/Rec matches record
  my.ns.Rec) and the `val` is a map, use the record factory to return a value.  Otherwise,
  nil."
  [tag val]
  (when-let [factory (and (map? val) (tag->factory tag))]
    (factory val)))

(defn- keep-first 
  "Returns first truthy result of lazily applying `f` to each of the elements of `xs`.
  Returns nil if no truthy result is found.  Unlike `keep`, will not return false."
  [f xs]
  (first (remove false? (keep f xs))))

(defn some-tag-reader
  "Takes any number of tag-reader functions and returns a composite tag-reader that applies
the tag-readers in order returning the first truthy result (or nil if none)."
  ([] (constantly nil))
  ([tag-reader] tag-reader)
  ([r1 r2] (fn [tag val] (or (r1 tag val) (r2 tag val))))
  ([r1 r2 r3] (fn [tag val] (or (r1 tag val) (r2 tag val) (r3 tag val))))
  ([r1 r2 r3 & more] (fn [tag val] (keep-first (fn [r] (r tag val)) (conj more r3 r2 r1)))))

(defn safe-tag-reader
  "Takes any number of tag-reader functions and returns a composite tag-reader which will
  either return a truthy value or throw an exception if the tag cannot be handled
  appropriately with the value."
  ([] throw-tag-reader)
  ([tag-reader] (some-tag-reader tag-reader throw-tag-reader))
  ([r1 r2] (some-tag-reader r1 r2 throw-tag-reader))
  ([r1 r2 r3] (some-tag-reader r1 r2 r3 throw-tag-reader))
  ([r1 r2 r3 & more] (apply some-tag-reader r1 r2 r3 (concat more (list throw-tag-reader)))))

(defn data-reader
  "Returns a data-reader for a particular tag derived from one or more tag-readers."
  ([tag tag-reader] (partial (safe-tag-reader tag-reader) tag))
  ([tag tag-reader & more-tag-readers] 
     (partial (apply safe-tag-reader tag-reader more-tag-readers) tag)))

(def tagged-default-reader 
  "Default data-reader for reading an EDN tagged literal as a Record.  If the tag corresponds to a
  known Record class (tag my.ns/Rec for class my.ns.Rec), use that Record's map-style factory on
  the given map value.  If the tag is unknown, use the generic miner.tagged.TaggedValue."  
  (some-tag-reader record-tag-reader ->TaggedValue))

(defn- record-name
  "Returns the record's name as a String given the class `record-class`."
  [record-class]
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
  (tag->factory (class->tag record-class)))

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

(defn- strip-tag4 [^String tag-str]
  ;; strips "#inst " (or "#uuid ") and quoted quotes to get plain string
  (.substring tag-str 7 (dec (.length tag-str))))

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

  clojure.lang.TaggedLiteral
  (edn-tag [this] (:tag this))
  (edn-str [this] (pr-str this))
  (edn-value [this] (:form this))

  clojure.lang.IRecord
  (edn-tag [this] (class->tag (class this)))
  (edn-str [this] (with-out-str (pr-tagged-record-on this *out*)))
  (edn-value [this] (into {} this))

  java.util.Date 
  (edn-tag [this] 'inst)
  (edn-str [this] (pr-str this))
  (edn-value [this] (strip-tag4 (pr-str this)))

  java.util.Calendar
  (edn-tag [this] 'inst)
  (edn-str [this] (pr-str this))
  (edn-value [this] (strip-tag4 (pr-str this)))

  java.sql.Timestamp
  (edn-tag [this] 'inst)
  (edn-str [this] (pr-str this))
  (edn-value [this] (strip-tag4 (pr-str this)))

  java.util.UUID
  (edn-tag [this] 'uuid)
  (edn-str [this] (pr-str this))  
  (edn-value [this] (strip-tag4 (pr-str this)))  )
  

;; In the REPL, you can use this to install the tagged-default-reader as the default:

(comment 

  (require '[miner.tagged :as tag])
  (alter-var-root #'clojure.core/*default-data-reader-fn* (constantly tag/tagged-default-reader))

)

;; However, it's probably better just to use the `miner.tagged/read` and `miner.tagged/read-string`
;; functions.
