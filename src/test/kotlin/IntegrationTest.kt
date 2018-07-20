package mongo

import app.Job
import app.SimplePayload
import app.TotalJobs
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.httpPut
import com.github.kittinunf.fuel.jackson.responseObject
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*


/**
 * Test cases
 *
 * - submit to different queues
 * - submit to invalid queues
 * - queue setup
 *
 *
 * TODO - hardcoded URLs, localhost, port etc.
 */

class IntegrationTest {

    @BeforeEach
    fun initKotlinMapper() {
        com.github.kittinunf.fuel.jackson.mapper.findAndRegisterModules()  // for ZonedDateTime
    }

    @BeforeEach
    fun createQueue() {
        val (_, qResp, _) = "http://localhost:7000/queue/testQ".httpPut().responseString()

        assertEquals(201, qResp.statusCode)
    }


    @AfterEach
    fun deleteQueue() {
        val (_, qResp, _) = "http://localhost:7000/queue/testQ".httpDelete().responseString()

        assertEquals(200, qResp.statusCode)
    }


    @Test
    @DisplayName("Adding a queue with the same name is idempotent")
    fun duplicateQueue() {
        val (_, qResp, _) = "http://localhost:7000/queue/testQ".httpPut().responseString()
        assertEquals(201, qResp.statusCode)
    }


    @Test
    @DisplayName("Removing a non-existent queue will return a 404")
    fun deleteMissingQueue() {
        val (_, qResp, _) = "http://localhost:7000/queue/missingQ".httpDelete().responseString()

        assertEquals(404, qResp.statusCode)  // assert in BeforeEach?
    }

    /**
     * delete and count test each other - multiple assertions as need to check both the status code and actual result
     */
    @Test
    @DisplayName("Deleting all jobs in a queue will result in an empty queue")
    fun deleteJobs() {

        val (_, deleteResponse, _) = "http://localhost:7000/jobs/testQ".httpDelete().responseString()
        val (_, countResponse, countResult) = "http://localhost:7000/jobsCount/testQ".httpGet().responseObject<TotalJobs>()

        assertAll("The delete call must return 200 and the queue must have zero entries",
                { assertEquals(200, deleteResponse.statusCode) },
                { assertEquals(200, countResponse.statusCode) },
                { assertEquals(TotalJobs(0), countResult.get()) }
                )
    }


    @Test
    fun addJob() {
        val (_, deleteResponse, _) = "http://localhost:7000/jobs/testQ".httpDelete().responseString()
        val (_, countResponse, countResult) = "http://localhost:7000/jobsCount/testQ".httpGet().responseObject<TotalJobs>()

        assertEquals(200, countResponse.statusCode)
        assertEquals(TotalJobs(0), countResult.get())

        val (_, response, addResult) = "http://localhost:7000/addjob/testQ".httpPost().responseString()

        assertEquals(202, response.statusCode)

        val (_, countResponse2, countResult2) = "http://localhost:7000/jobsCount/testQ".httpGet().responseObject<TotalJobs>()
        assertEquals(200, countResponse2.statusCode)
        assertEquals(TotalJobs(1), countResult2.get())
    }

    // add two jobs, get the first one...confirm it is the first and check the status of each
    // confirm only two on the queue

    // TODO selection for these tests relies on clock precision as it gets the first but could race if clock precision is too coarse
    @Test
    fun addJobsGetFirstForProcessing() {
        val (_, deleteResponse, _) = "http://localhost:7000/jobs/testQ".httpDelete().responseString()
        val (_, countResponse, countResult) = "http://localhost:7000/jobsCount/testQ".httpGet().responseObject<TotalJobs>()

        assertEquals(200, countResponse.statusCode)
        assertEquals(TotalJobs(0), countResult.get())

        val (_, _, add1) = "http://localhost:7000/addjob/testQ".httpPost().responseObject<Job<SimplePayload>>()  // TODO error handling
        val (_, _, add2) = "http://localhost:7000/addjob/testQ".httpPost().responseObject<Job<SimplePayload>>()

        val (_, countResponse2, countResult2) = "http://localhost:7000/jobsCount/testQ".httpGet().responseObject<TotalJobs>()
        assertEquals(200, countResponse2.statusCode)
        assertEquals(TotalJobs(2), countResult2.get())

        val (_, nextResp, getHead) = "http://localhost:7000/nextjob/testQ".httpGet().responseObject<Job<SimplePayload>>()

        assertEquals(200, nextResp.statusCode)

        val (_, countResponse3, countResult3) = "http://localhost:7000/jobsCount/testQ".httpGet().responseObject<TotalJobs>()
        assertEquals(200, countResponse3.statusCode)
        assertEquals(TotalJobs(2), countResult3.get())

        assertEquals(add1.component1()!!.jobId, getHead.component1()!!.jobId)
    }



    // do the empty queue test - add one, process and do a next get

    @Test
    fun getAllJobs() {
        val (_, _, add1) = "http://localhost:7000/addjob/testQ".httpPost().responseObject<Job<SimplePayload>>()  // TODO error handling
        val (_, _, add2) = "http://localhost:7000/addjob/testQ".httpPost().responseObject<Job<SimplePayload>>()

        val (_, resp, result) = "http://localhost:7000/jobs/testQ".httpGet().responseObject<List<Job<SimplePayload>>>()

        assertAll("",
                { assertEquals(200, resp.statusCode) },
                { assertEquals(2, result.component1()!!.size)}
        )
    }

    @Test
    @DisplayName("Query a job by the id created when it is submitted")
    fun getJobById() {
        val (_, addResp, addRes) = "http://localhost:7000/addjob/testQ".httpPost().responseObject<Job<SimplePayload>>()
        assertEquals( 202, addResp.statusCode)

        val id = addRes.component1()!!.jobId
        val (_, jobResp, jobResult) = "http://localhost:7000/job/testQ/id/$id".httpGet().responseObject<Job<SimplePayload>>()

        assertAll("Get id call must return 200 and have the same job id as originally added",
            { assertEquals(200, jobResp.statusCode) },
            { assertEquals(addRes.component1()!!.jobId, jobResult.component1()!!.jobId) }
        )
    }

    @Test
    fun cancelJob() {

        fail<Any>("")
    }
}