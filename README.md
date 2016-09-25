# Solr Mongo Importer
Welcome to the Solr Mongo Importer project. This project provides MongoDb support for the Solr Data Import Handler.
This is based on James' project (https://github.com/james75) with some improvement to make sure that deltaImportQuery
 and deletedPkQuery work with last_index_time

## Features
* Retrive data from a MongoDb collection
* Authenticate using MongoDb authentication
* Map Mongo fields to Solr fields
* Delta import available and works with last_index_itme
* deletedPkQuery available and works with last_index_itme

## Classes

* MongoDataSource - Provides a MongoDb datasource
    * database  (**required**) - The name of the data base you want to connect to
    * clientUri (**required** - simple example: mongodb://localhost:27017  complicated example: mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][?options]])
* MongoEntityProcessor - Use with the MongoDataSource to query a MongoDb collection
    * collection (**required**)
    * query (**required**)
    * deltaQuery (*optional*)
    * deltaImportQuery (*optional*)
* MongoMapperTransformer - Map MongoDb fields to your Solr schema
    * mongoField (**required**)

## Installation
1. Firstly you will need a copy of the Solr Mongo Importer jar.
    ### Getting Solr Mongo Importer
    1. [Download the latest JAR from github](https://github.com/harvshen/SolrMongoImporter/releases)
    2. Build your own using the ant build script you will need the JDK installed as well as Ant and Ivy, just run "ant build"
2. You will also need the [Mongo Java driver 2.x JAR]   (https://github.com/harvshen/mongo-java-driver/downloads)
   Or find the [local copy here] (https://github.com/harvshen/SolrMongoImporter/releases)

3. Place both of these jar's in your Solr libaries folder ( I put mine in 'dist' folder with the other jar's)
4. Add lib directives to your solrconfig.xml

```xml
    <lib path="../../dist/solr-mongo-importer-{version}.jar" />
    <lib path="../../dist/mongo-java-driver-2.x.jar" />
```

##Usage
Here is a sample data-config.xml showing the use of all components
```xml
<?xml version="1.0" encoding="utf-8" ?>
<dataConfig>
    <!--clientUri=mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][?options]] -->
    <dataSource name="MongoDS" type="MongoDataSource" database="posts" clientUri="mongodb://localhost:27017"/>
    <document name="import">
        <!-- if query="" then it imports everything -->
        <entity  processor="MongoEntityProcessor"
                 collection="sellposts"
                 datasource="MongoDS"
                 transformer="MongoMapperTransformer" name="sellpost"
                 query="{$or : [{'status' : 'AVAILABLE'},{'status' : 'SOLD'} ]}"
                 deltaQuery="{$and : [ {$or : [{'status' : 'AVAILABLE'},{'status' : 'SOLD'} ]}, {'modifiedAt':{$gt:{$date:'${dih.last_index_time}'}} } ] }"
                 deltaImportQuery="{'_id':'${dih.delta._id}'}"
                 deletedPkQuery="{$and : [ {$or : [{'status' : 'DELETED'},{'status' : 'UNLISTED'} ]}, {'modifiedAt':{$gt:{$date:'${dih.last_index_time}'}} } ] }"
                >

            <!--  If mongoField name and the field declared in schema.xml are the same, then you don't need to declare below field mapping.
                  If not same than you have to refer the mongoField to field in schema.xml
                 ( Ex: mongoField="EmpNumber" to name="EmployeeNumber").
                 <field column="EmpNumber" name="EmployeeNumber" mongoField="EmpNumber"/>
                 -->
            <field column="_id" name="postId"/>
            <field column="coordination_lattitude" name="GeoLocation_0_coordinate"/>
            <field column="coordination_longitude" name="GeoLocation_1_coordinate"/>
            <field column="price_value" name="price"/>
        </entity>
    </document>
</dataConfig>

```
