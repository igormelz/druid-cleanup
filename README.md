# druid-cleanup-service

Service for cleaning the local disk from indexed native-batch files and scheduled submit a kill task for permanently delete data from druid cluster after dropping segments. 

### Prerequisites

* JDK 1.8+
* Maven 3.5.3+ 
* Druid metadata storage on PostgreSQL (for MySQL see [configure](#configure-MySQL))

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

Add additional notes about how to deploy this on a live system

## Built With

* [Quarkus](https://quarkus.io/) 
* [Camel](http://camel.apache.org/) 
* [Maven](https://maven.apache.org/) 

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details

