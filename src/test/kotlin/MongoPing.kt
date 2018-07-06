package mongo

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
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

    @Test
    fun connectionTest()  {


        val client = KMongo.createClient() //get com.mongodb.MongoClient new instance
        val database = client.getDatabase("test") //normal java driver usage
        val col = database.getCollection<Jedi>() //KMongo extension method
//here the name of the collection by convention is "jedi"
//you can use getCollection<Jedi>("otherjedi") if the collection name is different

        col.insertOne(Jedi("Luke Skywalker", 19))

        val yoda : Jedi? = col.findOne(Jedi::name eq "Yoda")
        println(yoda)

        val luke : Jedi? = col.findOne(Jedi::name eq "Luke Skywalker")
        println(luke)
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

        val job = Job(startTime = LocalDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS),  // Mongo can only store to millis precision breaks "equals"
                        //createdOn = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS),
                        endTime = LocalDateTime.now().plusDays(4).truncatedTo(ChronoUnit.MILLIS),
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


        val secondJob = Job(startTime = LocalDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS),  // Mongo can only store to millis precision breaks "equals"
                createdOn = ZonedDateTime.of(2010, 1, 1, 1, 1, 0, 0, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MILLIS),
                endTime = LocalDateTime.now().plusDays(4).truncatedTo(ChronoUnit.MILLIS),
                jobRef = "second job",
                payload = SimplePayload("p1", "a user"))

        col.insertOne(secondJob)

        val firstJob = Job(startTime = LocalDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS),  // Mongo can only store to millis precision breaks "equals"
                createdOn = ZonedDateTime.of(2009, 1, 1, 1, 1, 0, 0, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MILLIS),
                endTime = LocalDateTime.now().plusDays(4).truncatedTo(ChronoUnit.MILLIS),
                jobRef = "first job",
                payload = SimplePayload("p1", "a user"))

        col.insertOne(firstJob)

        val completedJob = Job(startTime = LocalDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS),  // Mongo can only store to millis precision breaks "equals"
                createdOn = ZonedDateTime.of(2008, 1, 1, 1, 1, 0, 0, ZoneId.systemDefault()).truncatedTo(ChronoUnit.MILLIS),
                endTime = LocalDateTime.now().plusDays(4).truncatedTo(ChronoUnit.MILLIS),
                jobRef = "completed job",
                status = JobStatus.COMPLETED,
                payload = SimplePayload("p1", "a user"))

        col.insertOne(completedJob)


        col.find().forEach {
            println("${it.jobId} :: ${it.jobRef}")
        }

        val job5 = col.find().filter("""{status: "SUBMITTED"}""").sort("{createdOn: 1}").limit(1).first()

        println(job5)

        assertEquals(3, col.countDocuments())
        assertEquals(firstJob.jobId, job5.jobId)

    }


    @Test
    fun getData() {

            val client = KMongo.createClient() //get com.mongodb.MongoClient new instance
            val database = client.getDatabase("work") //normal java driver usage

            val col = database.getCollection<Job<SimplePayload>>("queue")

        col.find().forEach(::println)

        }
}


// data classes moved to be top level rather than nested classes in the unit test methods, as Jackson moans

// Jackson is able to cope with de-serialising generic types - such as Job<SimplePayload> the native method fails.

data class Jedi(val name: String, val age: Int)

// https://www.programmableweb.com/news/rest-api-design-put-type-content-type/2011/11/18
// See type tunneling - not the type but the format e.g. XML or JSON

data class Job<T>(var startTime : LocalDateTime, //Option<LocalDateTime> = None,
                  var createdOn : ZonedDateTime = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS),
                  var endTime : LocalDateTime, //Option<LocalDateTime> = None,
                  var priority: Int = 0,
                  val jobId : UUID = UUID.randomUUID(),  // default initialiser - ignored when de-serialised from the database
                  val contentType : String = "unknown",  // not in HTTP header as that would be JSON
                  val status : JobStatus = JobStatus.SUBMITTED,
                  val jobRef : String = "user job name",
                  var payload : T)

data class SimplePayload(val queryParam : String, val submitter : String)


sealed class Option<out A>
object None : Option<Nothing>()
data class Some<out B>(val value: B) : Option<B>()


enum class JobStatus {
    SUBMITTED,
    CANCELLED,
    COMPLETED,
    IN_PROGRESS
}

fun <TDocument> MongoCollection<TDocument>.find1(filter : Bson) : Option<TDocument> {
    val doc = findOne(filter)

    return if (doc == null) {
        None
    } else Some(doc)
}

