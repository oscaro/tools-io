# tools.io Changelog

## 0.3.25 (2022/05/15)
* use charred for csv serialization.
* fix write-jsons-file indentation error.

## 0.3.24 (2022/04/25)
* replace cheshire with charred.
* Bump deps: clojure (1.11.1), clj-yaml. data.csv.

## 0.3.23 (2021/12/13)
* Added `list-dirs` support.
* Bump tools.namespace.

## 0.3.22 (2021/08/19)
* Bump cheshire, clj-yaml

## 0.3.21 (2021/03/22)
* Bump clojure, data.csv, clj-yaml (now from clj-commons), tools.namespace

## 0.3.20 (2020/08/05)
* Bump cheshire

## 0.3.19 (2020/03/27)
* Configuration files can be loaded from any protocol
* Always use a slash when joining paths

## 0.3.18 (2019/11/21)
* Fix `read-string-files` options: they were ignored after the first file
* throw a more understandable exception for unsupported file protocol

## 0.3.17 (2019/03/27)
* repl: some reflection warnings removed
* multi-method `exists?` added to `tools.io.core` with its proxy in `tools.io`
* bump dependencies
* clojure 1.10 / java 11 compatibility

## 0.3.16 (2019/02/22)

First public version.
