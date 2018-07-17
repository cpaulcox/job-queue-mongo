package app

import com.mongodb.client.MongoDatabase
import org.litote.kmongo.KMongo
import kotlin.reflect.KProperty

/**
 * TODO - how to set credentials and the database name
 */

class MongoDb {



    companion object {
        lateinit var databaseName : String

        operator fun getValue(nothing: Nothing?, property: KProperty<*>): MongoDatabase {

            val client = KMongo.createClient() //get com.mongodb.MongoClient new instance
            return client.getDatabase(databaseName) //normal java driver usage
        }
    }
}