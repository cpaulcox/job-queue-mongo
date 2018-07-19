package mongo

import app.Job
import app.SimplePayload
import app.TotalJobs
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.httpPut
import com.github.kittinunf.fuel.jackson.responseObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


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


        assertEquals(201, qResp.statusCode)  // assert in BeforeEach?
    }


    @AfterEach
    fun deleteQueue() {
        val (_, qResp, _) = "http://localhost:7000/queue/testQ".httpDelete().responseString()


        assertEquals(200, qResp.statusCode)  // assert in BeforeEach?
    }


    @Test
    fun duplicateQueue() {
        val (_, qResp, _) = "http://localhost:7000/queue/testQ".httpPut().responseString()
        assertEquals(201, qResp.statusCode)
    }


    @Test
    fun deleteMissingQueue() {
        val (_, qResp, _) = "http://localhost:7000/queue/missingQ".httpDelete().responseString()


        assertEquals(404, qResp.statusCode)  // assert in BeforeEach?

    }
    /**
     * delete and count test each other - multiple assertions as need to check both the status code and actual result
     */
    @Test
    fun deleteJobs() {

        val (_, deleteResponse, _) = "http://localhost:7000/jobs/testQ".httpDelete().responseString()
        val (_, countResponse, countResult) = "http://localhost:7000/jobsCount/testQ".httpGet().responseObject<TotalJobs>()

        assertEquals(200, deleteResponse.statusCode)
        assertEquals(200, countResponse.statusCode)
        assertEquals(TotalJobs(0), countResult.get())
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

        println(add1.component1().toString())
        println(add2.component1().toString())

        val (_, countResponse2, countResult2) = "http://localhost:7000/jobsCount/testQ".httpGet().responseObject<TotalJobs>()
        assertEquals(200, countResponse2.statusCode)
        assertEquals(TotalJobs(2), countResult2.get())

        val (_, nextResp, getHead) = "http://localhost:7000/nextjob/testQ".httpGet().responseObject<Job<SimplePayload>>()

        assertEquals(200, nextResp.statusCode)
        println(getHead.component1())


        val (_, countResponse3, countResult3) = "http://localhost:7000/jobsCount/testQ".httpGet().responseObject<TotalJobs>()
        assertEquals(200, countResponse3.statusCode)
        assertEquals(TotalJobs(2), countResult3.get())

        assertEquals(add1.component1()!!.jobId, getHead.component1()!!.jobId)
    }



    // do the empty queue test - add one, process and do a next get

    @Test
    fun getJobsByStatus() {

        fail<Any>("")
    }

    @Test
    fun getJobById() {
        val (_, _, add1) = "http://localhost:7000/addjob/testQ".httpPost().responseObject<Job<SimplePayload>>()

        val id = add1.component1()!!.jobId
        val (_, jobResp, jobResult) = "http://localhost:7000/job/testQ/id/$id".httpGet().responseObject<Job<SimplePayload>>()


        assertEquals(200, jobResp.statusCode)
        assertEquals(add1.component1()!!.jobId, jobResult.component1()!!.jobId)

    }

    @Test
    fun cancelJob() {

        fail<Any>("")
    }


}