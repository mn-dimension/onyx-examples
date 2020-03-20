# global-kafka-windows

Demonstrates using the Global windowing feature with Kafka to work on OSX March 2020.

## Prerequisites:

This requires a non embedded Kafka and ZooKeeper. ZooKeeper must be running on 2181 (not 2188 as Onyx generally uses)
Kafka must run on port 127.0.0.1:9092

1. Install zookeeper; `brew install zookeeper` 
2. Install [jenv](https://github.com/jenv/jenv) via homebrew with `brew install jenv` and follow the [instructions](https://github.com/jenv/jenv#11-installing-jenv).
3. Install java version via homebrew `brew cask install homebrew/cask-versions/adoptopenjdk8`
4. Add the versions of java to jenv `jenv add $(/usr/libexec/java_home -v 1.8) && jenv add $(/usr/libexec/java_home)`
5. Install kafka with `brew install kafka`

## Cleaning out Zookeeper and Kafka structure

`rm -rf /usr/local/var/lib/zookeeper`
`rm -rf /usr/local/var/lib/kafka-logs*`

## Running

Run everything from the project directory where the correct Java version will be set by jenv local settings and each step in it's own terminal.

1. **Zookeeper**: `zookeeper-server-start /usr/local/etc/kafka/zookeeper.properties`
2. **Start Kafka**: ` kafka-server-start /usr/local/etc/kafka/server.properties`
3. **Create new input topic**: `kafka-topics --create --zookeeper localhost:2181 --topic my-input-message-stream --replication-factor 1 --partitions 1`. You can see the topics by `kafka-topics --list --zookeeper localhost:2181`
4. **Create new output topic**: `kafka-topics --create --zookeeper localhost:2181 --topic my-output-message-stream --replication-factor 1 --partitions 1`. You can see the topics by `kafka-topics --list --zookeeper localhost:2181`
5. **Run**: `lein run`
6. **Listen for some messages on the output topic**: `kafka-console-consumer --bootstrap-server localhost:9092 --topic my-output-message-stream`
7. **Send some messages on the input topic**: `kafka-console-producer --broker-list localhost:9092 --topic my-input-message-stream` and type some messages eg. 
`{:n 0 :event-time #inst "2015-09-13T03:00:00.829-00:00"}
{:n 1 :event-time #inst "2015-09-13T03:03:00.829-00:00"}
{:n 2 :event-time #inst "2015-09-13T03:07:00.829-00:00"}
{:n 3 :event-time #inst "2015-09-13T03:11:00.829-00:00"}
{:n 4 :event-time #inst "2015-09-13T03:15:00.829-00:00"}`

## Ouput

1. Each message on the output topic in 6
2. Concatenated messages from the onyx window output from 5

## License

Copyright © 2016 Distributed Masonry

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
