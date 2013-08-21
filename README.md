# tagged

A Clojure library for encoding Records as EDN tagged literals.

The EDN format does not directly support Clojure records.  This small library allows records to be
printed as tagged literals using a simple "marker" protocol.  Just add `EdnRecord` to the end of
your `defrecord` form and it will get a `print-method` that uses a data literal format instead of
the usual record format.  For example, the record class name `my.ns.Rec` is translated into the
literal tag `my.ns/Rec`.  That slight notational change, plus a convenient default data-reader
(`tagged-default-reader`), allows you to use records as EDN data.  The library also includes
variants of `clojure.edn/read` and `clojure.edn/read-string` with the `tagged-default-reader` set as
the `:default`.

The `tagged-default-reader` has a second feature.  It handles *unknown* tags using a `TaggedValue`
record which preserves the original print representation as a data literal.  This is convenient if
your program is interested only in a subset of the data it's processing and you want to preserve any
information that you don't understand.

This code was adapted from my presentation at *Clojure/West 2013*: **The Data-Reader's Guide to The
Galaxy**.  You're welcome to copy the source and modify it to suit your needs.

http://www.infoq.com/presentations/Clojure-Data-Reader


## Leiningen

Add the dependency to your project.clj:

    [com.velisco/tagged "0.2.1"]

I might forget to update the version number here in the README.  The latest version is available on
Clojars.org:

https://clojars.org/com.velisco/tagged


## Example


    (require '[miner.tagged :as tag])
	(require '[clojure.edn :as edn])
	
	(defrecord Basic [b])
	(pr-str (->Basic 101))
	;;->  "#user.Basic{:b 101}"
	
	(def b (edn/read-string "#user.Basic{:b 101}"))
    ;; Exception (with a misleading error message)

	;; add the EdnRecord "marker" protocol to the defrecord
	(defrecord Enhanced [e] tag/EdnRecord)
	(pr-str (->Enhanced 42))
	;;=> "#user/Enhanced {:e 42}"

	(def e (tag/read-string "#user/Enhanced {:e 42}"))
	(pr-str e)
	;;=> "#user/Enhanced {:e 42}"

	(:e e)
	;;=> 42

	(class e)
	;;=> user.Enhanced
	
	(def unknown (tag/read-string "#my.ns/Unknown 13"))
	(pr-str unknown)
	;;=> "#my.ns/Unknown 13"

	(class unknown)
	;;=> miner.tagged.TaggedValue

	(:tag unknown)
	;;=> my.ns/Unknown

	(:value unknown)
	;;=> 13
	

## Copyright and License

Copyright (c) 2013 Stephen E. Miner.

Distributed under the Eclipse Public License, the same as Clojure.
