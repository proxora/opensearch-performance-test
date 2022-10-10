#!/bin/bash
set -e

searchEngine=$1
version=$2
# Default Test round: 5x times
rounds=${3:-5}

if [[ "$searchEngine" != "elasticsearch" ]] && [[ "$searchEngine" != "opensearch" ]]; then
  echo "First parameter should be SearchEngine-Type: elasticsearch, opensearch !" && exit 1;
fi

if [ -z "$version" ]; then
  echo "Second parameter should be SearchEngine-Version (eg. 7.11.2) !" && exit 1;
fi

echo "# Using setup: "
echo " - $searchEngine $version   "
echo " - with $rounds test rounds "
echo

# Setting docker env-vars
export ELASTICSEARCH_VERSION_TAG=$version
export OPENSEARCH_VERSION_TAG=$version

echo "# Starting docker-compose ..."
docker-compose -f ./docker/$searchEngine/docker-compose.yml up -d
echo " @Await containers are up & running (10s) ..." && sleep 10
echo ""

echo "# Run performance-test app ($rounds rounds) ..."
for i in $(seq 1 $rounds)
do
  ./gradlew --quiet run -Psearchengine.provider=$searchEngine -P$searchEngine.version=$version
done
echo ""

echo "# Stopping docker-compose ..."
docker-compose -f ./docker/$searchEngine/docker-compose.yml down
echo ""
