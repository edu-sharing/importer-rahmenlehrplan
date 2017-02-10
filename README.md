# edu-sharing Importer Rahmenlehrplan

started as part of #hack4OER on July 2016 http://hackathon2016.edu-sharing-network.org

The idea is to import the XML data of the school curriculum of Berlin-Brandenburg and create from it a collection structure for OER (Open Educational Ressources) within an edu-sharing repo instance (using the REST API).

The school curriculum of Berlin-Brandenburg can be found in humanreadable version at:
http://bildungsserver.berlin-brandenburg.de/rlp-online/startseite/

XML Data Sources: https://github.com/hfreye/RLP-XML


## get it running

Clone git and import in Eclipse as a Maven Project.

The most important code is the `src/main/java/net/edusharing/collections/rlp/RLPCollectionImporter.java`

In the top area of the code enter the username, password and url of for edu-sharing account. Also you need to set the `lehrplanCollectionId` - this the root collection below which all other collections should get created. To get this id - go into your edu-sharing (create and) open the collection you want to use as root collection and look at the URL - use the `id` you see there for the `lehrplanCollectionId` value. Also check that the account you use has the proper rights and the root collection is empty. 

Then run the JAVA main script in Eclipse simply with "Run As > Java Application" ... and you should see in the console that it takes some time to create all that collections.


## preset the image, background color and more for a collection 

If you check the XML data source you can see that every element that will be converted into a collection has an ID. You also find this ID also at the start of every description of a collection created. You can use this ID to extend the configuration of the collection.

At the beginning of the script you can set the `nameOfExtraDataFile` variable. This is the name of a JSON file within the `collectionData` directory. This file contains a JSON array with objects like this

```json
{
	"id"	: "C-BIO",
	"image" : "C-CH.png",
	"color" : "#ff0000"
}
```

The value `id` needs to match the ID if the collection mentioned above. Set a color like in the example above. You can also name a image als icon for the collection - make sure to place the image file within the same directory as the JSON file.

If you want to extend the data structure simply add more key-value pairs to it. Then also add this value to the `ExtraData` class within the script. Then you can use this data to extend the JAVA script as you like.
