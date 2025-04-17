# tools.io Changelog

## [0.3.41] - 2025-04-17
### Changed
* Bump charred (1.037).

## [0.3.40] - 2025-02-03
### Changed
* Bump clj-taml (1.0.29), charred (1.036).

## [0.3.39] - 2024-10-04
### Changed
* bump clj-yaml (1.0.28), commons-compress (1.27.1)

## [0.3.38] - 2024-07-01
### Added
* support for more compression algorithms.

### Changed
* bump clojure 1.11.3, commons-compress 1.26.2.

## [0.3.37] - 2024-03-14
### Changed
* bump clojure 1.11.2, charred 1.034, commons-compress 1.26.1.

## [0.3.36] - 2023-12-14
### Changed
* bump charred 1.033, commons-compress 1.25.0.

## [0.3.35] - 2023-11-02
### Fixed
* read-jsons-file parameters to charred.

## [0.3.34] - 2023-11-02
### Changed
* Avoid deconstruction on sizeof default config.

## [0.3.33] - 2023-11-02
### Added
* Add `sizeof` function

## [0.3.32] - 2023-09-21
* fix parent of URI-like path.
* bump charred 1.032, commons-compress 1.24.0, clj-yaml 1.0.27

## [0.3.31] - 2023-04-16
* fix write-edn-file regression.

## [0.3.30] - 2023-04-07
* updated / fixed tests.
* bump charred 1.028, commons-compress 1.23.0
* added exported clj-kondo with hooks for with-tempdir/with-tempfile.

## [0.3.29] - 2023-01-24
* zip/unzip support.
* bump charred 1.019, commons-compress 1.22.

## [0.3.28] - 2022-12-02
* additional charred/read-json opts to match cheshire behavior.
* bump charred to 1.026, clj-yaml to 1.0.26.

## [0.3.27] - 2022-08-04
* add csv io tests.
* bump charred to 1.011.

## [0.3.26] - 2022-05-22
* bump charred and set options to resolve compatibility issues and
  match previous behavior.
* added json io tests.

## [0.3.25] - 2022-05-15
* use charred for csv serialization.
* fix write-jsons-file indentation error.

## [0.3.24] - 2022-04-25
* replace cheshire with charred.
* Bump deps: clojure (1.11.1), clj-yaml. data.csv.

## [0.3.23] - 2021-12-13
* Added `list-dirs` support.
* Bump tools.namespace.

## [0.3.22] - 2021-08-19
* Bump cheshire, clj-yaml

## [0.3.21] - 2021-03-22
* Bump clojure, data.csv, clj-yaml (now from clj-commons), tools.namespace

## [0.3.20] - 2020-08-05
* Bump cheshire

## [0.3.19] - 2020-03-27
* Configuration files can be loaded from any protocol
* Always use a slash when joining paths

## [0.3.18] - 2019-11-21
* Fix `read-string-files` options: they were ignored after the first file
* throw a more understandable exception for unsupported file protocol

## [0.3.17] - 2019-03-27
* repl: some reflection warnings removed
* multi-method `exists?` added to `tools.io.core` with its proxy in `tools.io`
* bump dependencies
* clojure 1.10 / java 11 compatibility

## [0.3.16] - 2019-02-22

First public version.
