# docs-sdk-scala
Documentation for the Couchbase Scala SDK.

## Contributions
Contributions are welcome!  Please test them locally (see below) and then submit a pull request.

### Testing changes locally
The documentation system Antora is used to build the docs.

Install Antora 2, then:

```antora staging-antora-playbook.yml```

The generated HTML is written to `public`.

Note that, as these docs are intended to be built as part of a wider documentation set, there will be a number of
errors both to stdout and in the generated output.  As long as these appear to relate to outside packages, they can
be safely ignored.