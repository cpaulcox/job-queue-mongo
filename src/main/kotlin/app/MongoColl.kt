package app

import com.mongodb.MongoClient
import com.mongodb.MongoClientOptions
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import org.litote.kmongo.KMongo
import kotlin.reflect.KProperty
import org.apache.logging.log4j.LogManager

/**

*/

class MongoColl<T>(val collection: String) {

    val db by MongoDb

    operator  fun getValue(coll: MongoCollection<*>, property: KProperty<*>): MongoCollection<*> {

        return db.getCollection(collection)
    }
    operator fun getValue(nothing: Nothing?, property: KProperty<*>): MongoCollection<T> {

        return db.getCollection(collection) as MongoCollection<T>
    }

}
