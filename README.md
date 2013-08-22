# tagged

A Clojure library for printing and reading Records as EDN tagged literals.

The [**EDN**](https://github.com/edn-format/edn) format does not directly support Clojure records.
This small library allows records to be printed and read as tagged literals which work with EDN.
Just implement a `print-method` that calls `miner.tagged/pr-tagged-record-on` and your record will
`pr` in a tagged literal format.  For example, the record class name `my.ns.Rec` is translated into
the literal tag `my.ns/Rec`.  That slight notational change, plus a convenient default data-reader
(`tagged-default-reader`), allows you to use records as EDN data.  The library also includes
variants of `read` and `read-string` with the `tagged-default-reader` set as the `:default`.

The `tagged-default-reader` has a second feature.  It handles *unknown* tags using a `TaggedValue`
record which preserves the original print representation as a tagged literal.  This is convenient if
your program is interested only in a subset of the data it's processing and you want to preserve any
information that you don't understand.

This code was adapted from my presentation at *Clojure/West 2013*:
[**The Data-Reader's Guide to The Galaxy**](http://www.infoq.com/presentations/Clojure-Data-Reader).
You're welcome to copy the source and modify it to suit your needs.


## Leiningen

Add the dependency to your project.clj:

    [com.velisco/tagged "0.3.0"]

I might forget to update the version number here in the README.  The latest version is available on
Clojars.org:

https://clojars.org/com.velisco/tagged


## Example


    (require '[miner.tagged :as tag])
	(require '[clojure.edn :as edn])
	
	(defrecord Rec [a])
	(def r1 (->Rec 1))

	(pr-str r1)
	;;-> "#user.Rec{:a 1}"
	
	(= r1 (edn/read-string (pr-str r1)))
    ;; Throws an exception with a misleading error message:
    ;;   RuntimeException No reader function for tag user.Rec  
	;;   clojure.lang.EdnReader$TaggedReader.readTagged (EdnReader.java:739)
	;; It's trying to say that the EDN reader does not support the record notation.

	;; Now implement the print-method
    (defmethod print-method user.Rec [this w] (tag/pr-tagged-record-on this w))

	(pr-str r1)
	;;=> "#user/Rec {:a 1}"
    ;; notice the format is now a tagged literal

	;; use the tag/read-string variant (namespace *tag* instead of *edn*) 
	;; to read the tagged record literal
	(= r1 (tag/read-string (pr-str r1)))
	;;=> true

	;; A second feature of the `tagged-default-reader` is handling *unknown* tags.
	(def unknown (tag/read-string "#my.ns/Unknown 13"))
	(pr-str unknown)
	;;=> "#my.ns/Unknown 13"
	;; The print representation is preserved, but the value is really a record.

	(class unknown)
	;;=> miner.tagged.TaggedValue

	(:tag unknown)
	;;=> my.ns/Unknown

	(:value unknown)
	;;=> 13
	

## Copyright and License

Copyright (c) 2013 Stephen E. Miner.

Distributed under the Eclipse Public License, the same as Clojure.
