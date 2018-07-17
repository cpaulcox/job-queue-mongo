package app

import com.mongodb.MongoClient
import com.mongodb.client.MongoDatabase
import org.litote.kmongo.KMongo
import kotlin.reflect.KProperty

/**
 * TODO - how to set credentials and the database name
 *
 *
 * https://medium.com/@mancebo128/the-right-way-to-connect-to-mongodb-from-a-java-ee-app-ae111c033eb6
 */

class MongoDb {



    companion object {
        lateinit var databaseName : String

        private lateinit var client : MongoClient

        operator fun getValue(nothing: Nothing?, property: KProperty<*>): MongoDatabase {

            // TODO cache this once established - don't create new clients each time

            client = KMongo.createClient() //get com.mongodb.MongoClient new instance
            //TODO KMongo.createClient  ... with servers list, credentials and Options

            return client.getDatabase(databaseName) //normal java driver usage
        }

        fun close() {
            client.close()
        }
    }
}