package app

import com.mongodb.MongoCommandException
import com.mongodb.MongoException
import com.mongodb.MongoServerException
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.*
import io.javalin.ApiBuilder.*
import io.javalin.Javalin
import io.javalin.event.EventType
import org.apache.logging.log4j.LogManager
import org.bson.Document
import org.litote.kmongo.eq
import org.litote.kmongo.findOne
import org.litote.kmongo.getCollection
import java.lang.IllegalStateException
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {



    MongoDb.apply {
        databaseName = "work"
        // TODO add hosts and/or connect URL
        clientOptions.description("job scheduler")
        clientOptions.applicationName("jobs")
        clientOptions.connectionsPerHost(10)
    }

    val logger = LogManager.getLogger("routes")

    val app = Javalin.create().apply {
        port(7000)
        exception(Exception::class.java) { e, _ -> e.printStackTrace() }
        //error(404) { ctx -> ctx.json("not found") }  generic error handler - can't override on a case-by-case basis?
    }.event(EventType.SERVER_STOPPING) {
        logger.info("Shutting down Mongo client")
        MongoDb.clientDelegate!!.close()
    }
    .start()

    addMongoExceptionHandlers(app)
    configureRoutes(app)
}

// exception handlers will pick the most specific class  https://javalin.io/documentation#exception-mapping
fun addMongoExceptionHandlers(app: Javalin) {
    val logger = LogManager.getLogger("MongoEexceptionLogger")


    app.apply {
        exception(IllegalStateException::class.java) { e, ctx ->
            logger.error(e.message)  // TODO needs the actual stack trace to indicate where the exception actually was generated
            ctx.status(500)
        }
        exception(MongoException::class.java) { e, ctx ->
            logger.error("${e::javaClass} ${e.code} ${e.message} ")  // TODO needs the actual stack trace to indicate where the exception actually was generated
            ctx.status(500)
        }
        exception(MongoServerException::class.java) { e, ctx ->
            logger.error("${e.code} ${e.serverAddress} ${e.message} ")  // TODO needs the actual stack trace to indicate where the exception actually was generated
            ctx.status(500)
        }
        exception(MongoCommandException::class.java) { e, ctx ->
            logger.error("${e.code} ${e.serverAddress} ${e.errorCode} ${e.message} ")  // TODO needs the actual stack trace to indicate where the exception actually was generated
            ctx.status(500)
        }
    }
}

fun configureRoutes(app : Javalin) {

    //val jobDB by MongoDb - can be used to initialise once and captured as a closure but as it is called for each access it adds little value?

    val logger = LogManager.getLogger("routes")

    app.routes {


        // Creates a queue - Mongo collection

        // TODO What is returned as errors e.g. duplicate queue or index or Mongo connection errors?
        // Admin role?
        put("/queue/:queue") {ctx ->
            val db by MongoDb

            val queue = ctx.param("queue") ?: "default"  // default avoids NPE but in reality can't happen due to URL parsing (404)

            try {
                db.createCollection(queue)

                val col = db.getCollection(queue)  // lots of queue config parameters can be supplied optionally

                // TTL index
                col.createIndex(Indexes.ascending("createdOn"), IndexOptions().expireAfter(1L, TimeUnit.MINUTES).name("TTL Index"))

                // Next Job Index
                col.createIndex(Indexes.ascending("createdOn", "status"), IndexOptions().name("Next Job Index"))

                col.createIndex(Indexes.ascending("jobId"), IndexOptions().name("Job ID Index"))  //randomness of indexes with UUIDs

            } catch (e: MongoCommandException) {  // TODO Needs finer grained error checking to deal with other error cases.
                logger.error("Duplicate queue creation for $queue")
            }

            ctx.status(201)  // Always 201 for duplicate creation as PUT is idempotent
        }

        // Creates a queue - Mongo collection

        // TODO What is returned as errors e.g. duplicate queue or index or Mongo connection errors?
        // Admin role?
        delete("/queue/:queue") {ctx ->
            val db by MongoDb

            val queue = ctx.param("queue") ?: "default"  // default avoids NPE but in reality can't happen due to URL parsing (404)

            if (!db.listCollectionNames().contains(queue)) {  // Cannot use getCollection as it lazily creates but with no configuration!
                ctx.status(404)
                ctx.result("Queue does not exist")
            }
            else {

                val col = db.getCollection(queue)  // lots of queue config parameters can be supplied optionally

                col.drop()
            }
        }


        // TODO should be a POST with a body NOT hardcoded
        post("/addjob/:queue") { ctx ->

            val db by MongoDb

            val queue = ctx.param("queue") ?: "default"

            val col = db.getCollection<Job<SimplePayload>>(queue)

            // ZonedDateTime.now( ZoneOffset.UTC ) // convert to UTC

            //println("UTC Time: ${ZonedDateTime.now( ZoneOffset.UTC )}")

            val job = Job(startTime = ZonedDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS).toString(),  // Mongo can only store to millis precision breaks "equals"
                    //createdOn = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS),
                    endTime = LocalDateTime.now().plusDays(4).truncatedTo(ChronoUnit.MILLIS).toString(),
                    payload = SimplePayload("p1", "paul"))

            col.insertOne(job)

            val job1 : Job<SimplePayload>? = col.findOne(Job<SimplePayload>::jobId eq job.jobId)

            ctx.json(job1 as Any)
            ctx.status(202)
        }


        get("/jobs/:queue") { ctx ->

            val queue = ctx.param("queue") ?: "default"  // TODO remove default

            val col by MongoColl<Document>(queue)

            val list = mutableListOf<Document>()

            col.find().forEach { list.add(it) }

//            ctx.contentType("application/json")
            ctx.json(list)
        }

        get("/job/:queue/id/:id") { ctx ->

            val queue = ctx.param("queue") ?: "default"  // TODO remove default
            val id = ctx.param("id") ?: "----"  // TODO remove default

            val col by MongoColl<Job<SimplePayload>>(queue)


            val job = col.find(Document("jobId", UUID.fromString(id)))


            if (job.none()) {
                ctx.status(404)
            }
            else {
                //ctx.contentType("application/json")
                ctx.json(job.first())
            }
        }


        get("/nextjob/:queue") {ctx ->

            val queue = ctx.param("queue") ?: "default"  // TODO remove default

            val db by MongoDb
            val col = db.getCollection<Job<SimplePayload>>(queue)
            val filter = Document("status", JobStatus.SUBMITTED.name)
            val update = Document("\$set", Document("status", JobStatus.IN_PROGRESS.name))
            val options = FindOneAndUpdateOptions().sort(Document("createdOn", 1))  // get latest

            // atomic select and update
            val original = col.findOneAndUpdate(filter, update, options )

            if (original == null) {
                ctx.contentType("text/plain")
                ctx.result("No jobs waiting")
                ctx.status(404)
            }
            else {
                ctx.status(200)
                ctx.json(original)
            }
        }

        delete("/jobs/:queue") {ctx ->
            val queue = ctx.param("queue") ?: "default"  // TODO remove default

            val db by MongoDb
            val col = db.getCollection<Job<SimplePayload>>(queue)

            col.deleteMany(Document())
        }

        get("/jobsCount/:queue") {ctx ->
            val queue = ctx.param("queue") ?: "default"  // TODO remove default

            val db by MongoDb
            val col = db.getCollection<Job<SimplePayload>>(queue)

            ctx.json(TotalJobs(col.countDocuments()))
        }
    }
}