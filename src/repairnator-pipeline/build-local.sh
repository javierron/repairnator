set -e

cd ../repairnator-core
mvn install -DskipTests
cd ../repairnator-pipeline
mvn package -DskipTests
cp target/repairnator-pipeline-3.3-SNAPSHOT-jar-with-dependencies.jar ../docker-images/pipeline-dockerimage/repairnator-pipeline.jar
cd ../docker-images/pipeline-dockerimage/
docker build . -t javierron/pipeline:3.0