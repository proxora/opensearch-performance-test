# General

This project can be used to check Opensearch/Elasticsearch performance for version upgrade.
The scope is query performance for text-data with `ngram(1,2)` analyzer


### Test scenario

Workflow
- start search-engine in docker 
  - (elastic + kibana) / (opensearch + dashboard)
- run test performance app (5x rounds)
  - create schema + data (1mio) // only once
  - query data (x1000)
  - display result
- stop docker setup


To start complete setup run (e.g.):
```sh
./start-test.sh <type> <version> [<rounds>]
./start-test.sh elasticsearch 7.11.2
./start-test.sh opensearch 2.3.0
```

# Run the test

Just start the elasticsearch instance and run the class ElasticPerformance/OpensearchPerformance.