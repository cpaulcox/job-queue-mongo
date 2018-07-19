package mongo

import app.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.FindOneAndUpdateOptions
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import org.bson.Document
import org.bson.conversions.Bson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.litote.kmongo.* //NEEDED! import KMongo extensions
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.litote.kmongo.MongoOperator.*
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

class MongoPing {

    @BeforeEach
    fun initKotlinMapper() {
        val mapper = jacksonObjectMapper()
    }

    // From http://learnmongodbthehardway.com/schema/queues/

    @Test
    fun jobQueue() {

        val client = KMongo.createClient() //get com.mongodb.MongoClient new instance
        val database = client.getDatabase("work") //normal java driver usage


        val col = database.getCollection<Job<SimplePayload>>("queue")
        col.drop()
        col.createIndex(Indexes.ascending("createdOn"),
                        IndexOptions().expireAfter(1L, TimeUnit.MINUTES))

        col.createIndex(Indexes.ascending("createdOn", "status"))


        // ZonedDateTime.now( ZoneOffset.UTC ) // convert to UTC

        println("UTC Time: ${ZonedDateTime.now( ZoneOffset.UTC )}")

        val job = Job(startTime = ZonedDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS).toString(),  // Mongo can only store to millis precision breaks "equals"
                        //createdOn = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS),
                        endTime = LocalDateTime.now().plusDays(4).truncatedTo(ChronoUnit.MILLIS).toString(),
                        payload = SimplePayload("p1", "paul"))

        col.insertOne(job)

        val job1 : Job<SimplePayload>? = col.findOne(Job<SimplePayload>::jobId eq job.jobId)

        assertEquals(job.jobId, job1?.jobId)

        //val job2 = col.updateOne(Job::priority eq 0, set(Job::startTime, LocalDateTime.now()))
        val job2 = col.updateOne(Job<SimplePayload>::priority eq 0, set(Job<SimplePayload>::startTime, LocalDateTime.now()))
        println(job2)

        // TODO find a clearer way of updating multiple fields and dates...
        val resp = col.updateOne("{priority: 0 }",
                """{$set:{"priority": 1,
                    |"payload.submitter": "frank",
                    |"endTime": new Date("${ZonedDateTime.now().plusMonths(4).format(DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss z", Locale.UK))}")}
            |}""".trimMargin())

        println(resp)


        val job3 : Job<SimplePayload>? = col.findOne(Job<SimplePayload>::priority eq 1)

        println(job3)
        assertEquals(job.jobId, job3?.jobId)  // jobs3 can be null

        col.find().forEach(::println)

        val job4 = col.find1(Job<SimplePayload>::priority eq 1)

        when (job4) {
            None -> println("Not found")
            is Some<Job<*>> -> println("Monad... ${job4.value.priority}")  // smart cast ignores payload type
        }



    }

    @Test
    fun headOfQueue() {
        val client = KMongo.createClient() //get com.mongodb.MongoClient new instance
        val database = client.getDatabase("work") //normal java driver usage


        val col = database.getCollection<Job<SimplePayload>>("queue")
        col.drop()
        col.createIndex(Indexes.ascending("createdOn"),
                IndexOptions().expireAfter(1L, TimeUnit.MINUTES))

        col.createIndex(Indexes.ascending("createdOn", "status"))


        val secondJob = Job(startTime = ZonedDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS).toString(),  // Mongo can only store to millis precision breaks "equals"
                createdOn = ZonedDateTime.of(2010, 1, 1, 1, 1, 0, 0, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MILLIS).toString(),
                endTime = LocalDateTime.now().plusDays(4).truncatedTo(ChronoUnit.MILLIS).toString(),
                jobRef = "second job",
                payload = SimplePayload("p1", "a user"))

        col.insertOne(secondJob)

        val firstJob = Job(startTime = ZonedDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS).toString(),  // Mongo can only store to millis precision breaks "equals"
                createdOn = ZonedDateTime.of(2009, 1, 1, 1, 1, 0, 0, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MILLIS).toString(),
                endTime = LocalDateTime.now().plusDays(4).truncatedTo(ChronoUnit.MILLIS).toString(),
                jobRef = "first job",
                payload = SimplePayload("p1", "a user"))

        col.insertOne(firstJob)

        val completedJob = Job(startTime = ZonedDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS).toString(),  // Mongo can only store to millis precision breaks "equals"
                createdOn = ZonedDateTime.of(2008, 1, 1, 1, 1, 0, 0, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MILLIS).toString(),
                endTime = LocalDateTime.now().plusDays(4).truncatedTo(ChronoUnit.MILLIS).toString(),
                jobRef = "completed job",
                status = JobStatus.COMPLETED,
                payload = SimplePayload("p1", "a user"))

        col.insertOne(completedJob)

        val filter = Document("status", JobStatus.SUBMITTED.name)
        val update = Document("status", JobStatus.IN_PROGRESS.name)
        val update2 = Document("\$set", update)
        val options = FindOneAndUpdateOptions().sort(Document("createdOn", 1))
        //val sort = Document("createdOn", 1)

        val found = col.find(filter)
        println(found.count())

        val original = col.findOneAndUpdate(filter, update2, options )

        col.find().forEach {
            println("${it.jobId} :: ${it.jobRef} ${it.status}")
        }

        val job5 = col.find().filter("""{status: "IN_PROGRESS"}""").sort("{createdOn: 1}").limit(1).first()

        println(job5)
        println(original)

        assertEquals(3, col.countDocuments())
        assertEquals(firstJob.jobId, job5.jobId)

   }
}

