# oscaro-tools-io

Oscaro’s generic I/O tools collection.

## Usage

```clojure
[com.oscaro/tools-io "0.3.18"]
```

[API docs](https://cljdoc.org/d/com.oscaro/tools-io/0.3.17/api/tools.io)

### `core/gzipped?`

Test if a filename ends with `.gz` or `.gzip`

**arguments**:
- filename

**returns**: a boolean

examples
```clojure
(core/gzipped? "toto.gz"); => true
(core/gzipped? "toto.GZip"); => true
```

### `join-path`

Join multiple parts of a path, like `os.path.join` in Python.

```clojure
(join-path "foo" "bar") ; => "foo/bar"
(join-path "foo/" "bar") ; => "foo/bar"
(join-path "gs://mybucket" "bar") ; => "gs://mybucket/bar"
```

### `basename`

```clojure
(basename "/var/log/mysql/") ; => "mysql"
(basename "http://www.google.com/index.html") ; => "index.html"
```

### `parent`

```clojure
(parent "/var/log/mysql/") ; => "/var/log"
(parent "http://www.google.com/index.html") ; => "http://www.google.com"
```

### `splitext`

```clojure
(splitext "http://www.google.com/index.html") ; => ["http://www.google.com/index" "html"]
(splitext "archive.tar.gz") ; => ["archive.tar" "gz"]
```

### `read-text-file`

return a lazy seq of string from a [protocol://]jsons[.gz] file.
*warning*: the seq must be entirely consumed before the file is closed.

**arguments**:
- filename: string

**returns**: an lazy seq of string

### `read-jsons-file`

return a lazy seq of parsed json objects from a [protocol://]jsons[.gz] file.
*warning*: the seq must be entirely consumed before the file is closed.

**arguments**:
- filename: string

**returns**: an lazy seq of parsed objects

example
```clojure
(doall (map println (read-jsons-file "sample.jsons.gz")))
```

### `read-edns-file`

return a lazy seq of parsed edn objects from a [protocol://]edn[.gz] file.
*warning*: the seq must be entirely consumed before the file is closed.

**arguments**:
- filename: string

**returns**: an lazy seq of parsed objects

### `read-csv-file`

return a lazy seq of parsed csv row as vector from a [protocol://]file.csv[.gz] file.
see http://clojure.github.io/data.csv/ for options.
*warning*: the seq must be entirely consumed before the file is closed.

**arguments**:
- filename: string
- args: options for data.csv  `{:separator (default \,) :quote (default \")}`

**returns**: an lazy seq of parsed objects

### `read-jsons-files`

return a lazy seq of parsed json objects from [protocol://]jsons[.gz] files.
*warning*: the seq must be entirely consumed before every files are closed.

**arguments**:
- [filenames]

**returns**: an lazy seq of parsed objects

example
```clojure
(doall (map println (read-jsons-files ["part1.jsons.gz" "part1.jsons.gz"])))
```

### `read-edns-files`

return a lazy seq of parsed json objects from [protocol://]jsons[.gz] files.
*warning*: the seq must be entirely consumed before every files are closed.

**arguments**:
- [filenames]

**returns**: an lazy seq of parsed objects

### `list-files`

return a seq of filenames beginning with provided path.
support local files and google storage with `gs://` as prefix, and an optional `:storage` as options

**arguments**:
- path
- [options]

**returns**: seq of string

examples
```clojure
(doall (map println (list-files "gs://my-bucket/dir/20160902/animals")))
;-> output:
;gs://my-bucket/dir/20160902/animals-aaaaaaaaaa.jsons.gz
;gs://my-bucket/dir/20160902/animals-aaaaaaaaab.jsons.gz
;gs://my-bucket/dir/20160902/animals-aaaaaaaaac.jsons.gz
;gs://my-bucket/dir/20160902/animals-aaaaaaaaad.jsons.gz
;gs://my-bucket/dir/20160902/animals-aaaaaaaaae.jsons.gz


(doall (map println (list-files "/home/alice/dir/20160902/animals")))
;-> output:
;/home/alice/dir/20160902/animals-aaaaaaaaaa.jsons.gz
;/home/alice/dir/20160902/animals-aaaaaaaaab.jsons.gz
;/home/alice/dir/20160902/animals-aaaaaaaaac.jsons.gz
;/home/alice/dir/20160902/animals-aaaaaaaaad.jsons.gz
;/home/alice/dir/20160902/animals-aaaaaaaaae.jsons.gz
```

### `load-config-file`

read and parse a configuration file.
edn, clj, json, js, yaml, yml supported.

*note*: if filename is a string, its searched first in resources, then locally

**arguments**:
- filename  (string or io/resource or io/file)

**returns**: an object


### `core/file-reader`

return a file as map like `{:stream clojure.java.io/reader}`, with support for [protocol://]file and file.gz.
you need to call (close! file) when you done.

**arguments**:
- filename
- [options]: by default options = {encoding "UTF-8"}

**returns**: an map with a `:stream` key

### `copy`

Copy file from source to destination.

**arguments**
- from
- from-opts
- to
- to-opts
- [copy-opts]: by defaults copy-opts = {buffer-size 1024}

example

Text file:
```clojure
(copy
  "/tmp/windows-file.csv"
  {:encoding "windows-1252"}
  "gs://my-bucket/dir/uf8-file.csv"
  {:encoding "UTF-8" :mime-type "text/csv"}
  {:buffer-size 2048})
```

For binary file, you must use an 8-bits encoding:
```clojure
(def byte-encoding "ISO-8859-1")
(copy
  "/tmp/workbook.xlsx"
  {:encoding byte-encoding}
  "gs://my-bucket/dir/uf8-file.csv"
  {:encoding byte-encoding :mime-type "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"}
  {:buffer-size 2048})
```

### `rm-rf`

Recursively remove a directory.

```clojure
(rm-rf "/path/to/my/directory")
```

### `with-tempfile`

Create a temporary file and remove it at the end of the body.

```clojure
(with-tempfile [filename]
  (println "There's a file called" filename "."))
(println "The file is now gone.")
```

### `with-tempdir`

Create a temporary directory and remove it at the end of the body.

```clojure
(with-tempdir [dirname]
  (println "There's a directory called" dirname "."))
(println "The directory is now gone.")
```

### `slurp`

### `spit`

### `exists?`

Test if a file exists.

```clojure
(exists? "https://oscaro.com") ;=> true
(exists? "local-file-that-do-not-exists") ;=> false
```

## License

Copyright © 2016-2019 Oscaro.com

Distributed under the Eclipse Public License either version 1.0 or (at your
option) any later version.
