# Contributing to `tools-io`

## Tests

Run the tests with:
```shell
lein test
```

## Make a release

1. `lein test`
2. Ensure the `CHANGELOG.md` is up to date
3. Change the version in `project.clj`
4. Commit and tag
5. Merge `devel` in `master`
6. `lein test` again just to be sure
7. `lein deploy clojars` (ensure you have a Clojars account member of the [org](https://clojars.org/groups/com.oscaro))
8. Go back on `devel` and change the version to the next snapshot
