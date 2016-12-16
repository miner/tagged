# tagged

A Clojure library for printing and reading Records as EDN tagged literals.

The [**EDN**](https://github.com/edn-format/edn) format does not directly support Clojure
records.  This small library allows records to be printed and read as tagged literals which
work with EDN.  Just implement a `print-method` that calls
`miner.tagged/pr-tagged-record-on` and your record will `pr` in a tagged literal format.
For example, the record class name `my.ns.Rec` is translated into the literal tag
`my.ns/Rec`.  That slight notational change, plus a convenient default data-reader
(`tagged-default-reader`), allows you to use records as EDN data.  The library also includes
variants of `read` and `read-string` with the `tagged-default-reader` set as the `:default`.

The `tagged-default-reader` has a second feature.  It handles *unknown* tags using a `TaggedValue`
record which preserves the original print representation as a tagged literal.  This is convenient if
your program is interested only in a subset of the data it's processing and you want to preserve any
information that you don't understand.

I use the term *tag-reader* to describe a function taking two args, the tag symbol and a
value, like a `*default-data-reader-fn*`.  Unlike a [data-reader][reader], a tag-reader may
return nil if it does not want to handle a particular value. (See CLJ-1138 for more
information about why a data-reader is not allowed to return nil.) The tag-reader convention
makes is simpler to compose multiple tag-reader functions using `some-tag-reader`. You can
wrap one or more tag-readers to create a data-reader with `data-reader`.  The
`throw-tag-reader` always throws so it's appropriate to use as your last resort tag-reader.

[reader]: http://clojure.org/reader#The%20Reader--Tagged%20Literals

The `tagged-default-reader` is actually composed of a couple of *tag-readers*.  The tagged
literal format for records is read with the `record-tag-reader`.  If that returns nil, then
the tagged literal is read as a clojure.lang.TaggedLiteral.

    Note that the `miner.tagged/->TaggedValue` factory function that was previously used in this
    library is now obsolete. Clojure added a similar function named `tagged-literal` which
    replaces it.  

The `tagged-literal` function works nicely as a tag-reader.  It handles the case of an
"unknown" tag.  The definition of `tagged-default-reader` is just:

    (def tagged-default-reader (some-tag-reader record-tag-reader tagged-literal))

You may want to combine your own tag-readers with `record-tag-reader` to make a more
sophisticated `*default-data-reader-fn*`.  Users of your library might want to use
tag-readers provided by your library for making new tag-readers.

This code was adapted from my presentation at *Clojure/West 2013*:
[**The Data-Reader's Guide to The Galaxy**](http://www.infoq.com/presentations/Clojure-Data-Reader).
You're welcome to copy the source and modify it to suit your needs.

## Leiningen

Add the dependency to your project.clj:

[![Tagged on clojars.org][latest]][clojar]

[latest]: https://clojars.org/com.velisco/tagged/latest-version.svg "Tagged on clojars.org"
[clojar]: https://clojars.org/com.velisco/tagged


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
	;; The print representation is preserved, but the value is a clojure.lang.TaggedLiteral.

	(class unknown)
	;;=> clojure.lang.TaggedLiteral

    (tagged-literal? unknown)
	;;=> true
	
	(:tag unknown)
	;;=> my.ns/Unknown

	(:form unknown)
	;;=> 13
	

## Copyright and License

Copyright (c) 2013 Stephen E. Miner.

Distributed under the Eclipse Public License, the same as Clojure.
