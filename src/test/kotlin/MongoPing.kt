package mongo

import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.litote.kmongo.* //NEEDED! import KMongo extensions
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.litote.kmongo.MongoOperator.set
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

class MongoPing {

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

        val job = Job(startTime = LocalDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS),  // Mongo can only store to millis precision breaks "equals"
                        createdOn = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
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

data class Job<T>(var startTime : LocalDateTime,
                  var createdOn : LocalDateTime,
                  var endTime : LocalDateTime,
                  var priority: Int = 0,
                  val jobId : UUID = UUID.randomUUID(),  // default initialiser - ignored when de-serialised from the database
                  var payload : T)

data class SimplePayload(val queryParam : String, val submitter : String)