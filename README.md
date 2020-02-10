# druid-cleanup

Utility service for scheduled cleaning the local disk from ingested batch files and permanently delete data from druid cluster for retention segments. 

### Prerequisites

* JDK 1.8+
* Maven 3.5.3+ 
* Druid metadata storage on PostgreSQL

### Build

```
# git clone 
# mvn clean install
```

### Install 

```
# java -jar target/*-running.jar
```

## Deployment 

* Must running on the same host that running indexing task. 
* Use options in `application.properties` to configure druid datastore connection, path to indexing-log and set period to run cleanup task.  

## Built With

* [Quarkus](https://quarkus.io/) 
* [Camel](http://camel.apache.org/) 
* [Maven](https://maven.apache.org/) 

## License

This project is licensed under the Apache 2.0 License - see the [LICENSE](LICENSE) file for details

