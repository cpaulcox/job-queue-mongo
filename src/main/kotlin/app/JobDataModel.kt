package app

import com.mongodb.client.MongoCollection
import org.bson.conversions.Bson
import org.litote.kmongo.findOne
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

// data classes moved to be top level rather than nested classes in the unit test methods, as Jackson moans

// Jackson is able to cope with de-serialising generic types - such as Job<SimplePayload> the native method fails.


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

