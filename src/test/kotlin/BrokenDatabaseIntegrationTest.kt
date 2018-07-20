package mongo

import app.MongoDb
import app.SimpleMongoClient
import app.addMongoExceptionHandlers
import app.configureRoutes
import com.github.kittinunf.fuel.httpPut
import com.mongodb.MongoClient
import com.mongodb.MongoClientOptions
import com.mongodb.client.MongoDatabase
import io.javalin.Javalin
import io.javalin.event.EventType
import org.apache.logging.log4j.LogManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.litote.kmongo.KMongo

class BrokenDatabaseIntegrationTest {



    @Test
    @DisplayName("Attempt to create a queue with a closed Mongo connection")
    fun createQueue() {
        val (_, qResp, _) = "http://localhost:7000/queue/testQ".httpPut().responseString()

        assertEquals(500, qResp.statusCode)
    }


    companion object {

        lateinit var app : Javalin

        @BeforeAll
        @JvmStatic
        fun startServer() {
            app = brokenMain(emptyArray())
        }

        @AfterAll
        @JvmStatic
        fun stopServer() {
            app.stop()
        }

    }

}


fun brokenMain(args: Array<String>) : Javalin {



    MongoDb.apply {
        databaseName = "broken"
        clientOptions.connectionsPerHost(1)


    }

    MongoDb.clientDelegate = BrokenMongoClient()  // replaces the default delegate


    val logger = LogManager.getLogger("routes")

    val app = Javalin.create().apply {
        port(7000)
        exception(Exception::class.java) { e, _ -> e.printStackTrace() }
        //error(404) { ctx -> ctx.json("not found") }  generic error handler - can't override on a case-by-case basis?
    }.event(EventType.SERVER_STOPPING) {
        logger.info("Shutting down Mongo client")
        MongoDb.clientDelegate!!.close()
    }.start()

    addMongoExceptionHandlers(app)

    configureRoutes(app)

    return app
}


class BrokenMongoClient : SimpleMongoClient {

    private val logger = LogManager.getLogger(this::class)

    private var client : MongoClient? = null // fudge around lateinit

    override fun initialiseClient(databaseName: String, clientOptions: MongoClientOptions.Builder) : MongoDatabase {
        // TODO create client should be thread-safe to avoid racing

        if (client == null) {
            client = KMongo.createClient("localhost", clientOptions.build())

            logger.info("Mongo Client Created")
            logger.info("Client settings: ${client!!.mongoClientOptions}")
        }
        //TODO KMongo.createClient  ... with servers list, credentials and Options

        val db =  (client as MongoClient).getDatabase(databaseName) //normal java driver usage

        client?.close()   // force a close sso should generate errors

        return db
    }

    override fun close() {
        client?.close()
    }
}
