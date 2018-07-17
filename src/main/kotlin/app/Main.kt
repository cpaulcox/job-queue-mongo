package app

import app.user.User
import app.user.UserDao
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import io.javalin.ApiBuilder.*
import io.javalin.Javalin
import io.javalin.event.EventType
import org.apache.logging.log4j.LogManager
import org.litote.kmongo.eq
import org.litote.kmongo.findOne
import org.litote.kmongo.getCollection
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {


    val logger = LogManager.getLogger("Main")
    MongoDb.apply {
        databaseName = "work"
    }

    val jobDB by MongoDb

    val userDao = UserDao()

    val app = Javalin.create().apply {
        port(7000)
        exception(Exception::class.java) { e, ctx -> e.printStackTrace() }
        error(404) { ctx -> ctx.json("not found") }
    }.event(EventType.SERVER_STOPPING) {
        logger.info("Shutting down Mongo client")
        MongoDb.close()
    }

    .start()

    app.routes {

        get("/addjob") { ctx ->

            val col = jobDB.getCollection<Job<SimplePayload>>("queue")
            col.drop()
            col.createIndex(Indexes.ascending("createdOn"),
                    IndexOptions().expireAfter(1L, TimeUnit.MINUTES))

            col.createIndex(Indexes.ascending("createdOn", "status"))


            // ZonedDateTime.now( ZoneOffset.UTC ) // convert to UTC

            //println("UTC Time: ${ZonedDateTime.now( ZoneOffset.UTC )}")

            val job = Job(startTime = LocalDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS),  // Mongo can only store to millis precision breaks "equals"
                    //createdOn = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS),
                    endTime = LocalDateTime.now().plusDays(4).truncatedTo(ChronoUnit.MILLIS),
                    payload = SimplePayload("p1", "paul"))

            col.insertOne(job)

            val job1 : Job<SimplePayload>? = col.findOne(Job<SimplePayload>::jobId eq job.jobId)

            col.find().forEach { logger.info(">>>>>>>>>>>>>>>>>>>>>>>> $it") }

            ctx.json(job1 as Any)

        }

        get("/jobs") { ctx ->
            val col = jobDB.getCollection<Job<SimplePayload>>("queue")


            val list = mutableListOf<Job<SimplePayload>>()
            logger.debug("?????????????????????   BEFORE ${col.find().count()}")
            col.find().forEach { list.add(it) }
            logger.debug("?????????????????????   AFTER ${col.find().count()}")
            ctx.contentType("application/json")
            ctx.json(list)
        }

        get("/users") { ctx ->
            ctx.json(userDao.users)
        }

        get("/users/:id") { ctx ->
            ctx.json(userDao.findById(ctx.param("id")!!.toInt())!!)
        }

        get("/users/email/:email") { ctx ->
            ctx.json(userDao.findByEmail(ctx.param("email")!!)!!)
        }

        post("/users/create") { ctx ->
            val user = ctx.bodyAsClass(User::class.java)
            userDao.save(name = user.name, email = user.email)
            ctx.status(201)
        }

        patch("/users/update/:id") { ctx ->
            val user = ctx.bodyAsClass(User::class.java)
            userDao.update(
                    id = ctx.param("id")!!.toInt(),
                    user = user
            )
            ctx.status(204)
        }

        delete("/users/delete/:id") { ctx ->
            userDao.delete(ctx.param("id")!!.toInt())
            ctx.status(204)
        }

    }

}
