package app

import com.mongodb.MongoClient
import com.mongodb.MongoClientOptions
import com.mongodb.client.MongoDatabase
import org.litote.kmongo.KMongo
import kotlin.reflect.KProperty
import org.apache.logging.log4j.LogManager

/**
 * TODO - how to set credentials and the database name
 *
 *
http://mongodb.github.io/mongo-java-driver/3.8/driver/tutorials/connect-to-mongodb/

"
important

Typically you only create one MongoClient instance for a given MongoDB deployment (e.g. standalone, replica set, or a sharded cluster) and use it across your application. However, if you do create multiple instances:

All resource usage limits (e.g. max connections, etc.) apply per MongoClient instance.

To dispose of an instance, call MongoClient.close() to clean up resources.
"


 */

class MongoDb {

    companion object {

        val logger = LogManager.getLogger(this::class)

        val clientOptions = MongoClientOptions.builder()

        lateinit var databaseName : String

        private var client : MongoClient? = null // fudge around lateinit

        operator fun getValue(nothing: Nothing?, property: KProperty<*>): MongoDatabase {

            // TODO create client should be thread-safe to avoid racing

            if (client == null) {
                client = KMongo.createClient("localhost", clientOptions.build())

                logger.info("Mongo Client Created")
                logger.info("Client settings: ${client!!.mongoClientOptions}")
            }
            //TODO KMongo.createClient  ... with servers list, credentials and Options

            return (client as MongoClient).getDatabase(databaseName) //normal java driver usage
        }

        fun close() {
            client?.close()
        }
    }
}