import io.gatling.core.Predef._
import io.gatling.http.Predef._

import java.util.UUID
import scala.concurrent.duration._

class FinCoreBankingSimulation extends Simulation {

  private val baseUrl = sys.env.getOrElse("FINCORE_BASE_URL", "http://localhost:8080")
  private val loginEmail = sys.env.getOrElse("FINCORE_TEST_EMAIL", "user@fincore.com")
  private val loginPassword = sys.env.getOrElse("FINCORE_TEST_PASSWORD", "password")
  private val sourceAccountId = sys.env.getOrElse("FINCORE_SOURCE_ACCOUNT_ID", "1")
  private val destinationAccountId = sys.env.getOrElse("FINCORE_DESTINATION_ACCOUNT_ID", "2")
  private val transferAmount = sys.env.getOrElse("FINCORE_TRANSFER_AMOUNT", "100.00")

  private val httpProtocol = http
    .baseUrl(baseUrl)
    .contentTypeHeader("application/json")
    .acceptHeader("application/json")

  private val credentialsFeeder = Iterator.continually(
    Map(
      "email" -> loginEmail,
      "password" -> loginPassword
    )
  )

  private val transferBody =
    s"""
       |{
       |  "type": "TRANSFER",
       |  "amount": $transferAmount,
       |  "sourceAccountId": $sourceAccountId,
       |  "destinationAccountId": $destinationAccountId
       |}
       |""".stripMargin

  private val scn = scenario("FinCore Transfer Load")
    .feed(credentialsFeeder)
    .exec(
      http("login")
        .post("/api/auth/login")
        .body(StringBody("""{"email":"#{email}","password":"#{password}"}"""))
        .check(status.is(200))
        .check(jsonPath("$.token").saveAs("accessToken"))
    )
    .repeat(5, "transferIndex") {
      exec(session => session.set("idempotencyKey", UUID.randomUUID().toString))
        .exec(
          http("transfer-#{transferIndex}")
            .post("/api/transactions/transfer")
            .header("Authorization", "Bearer #{accessToken}")
            .header("X-Idempotency-Key", "#{idempotencyKey}")
            .body(StringBody(transferBody))
            .check(status.in(200, 201))
        )
    }

  setUp(
    scn.inject(rampUsers(500).during(30.seconds))
  ).protocols(httpProtocol)
    .assertions(
      global.responseTime.percentile4.lt(500),
      global.failedRequests.percent.lt(1.0)
    )
}
