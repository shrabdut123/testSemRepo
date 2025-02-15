/*
## Overview
The `reserveArticles` function is part of the `ReservationsFacade` class, designed to handle the reservation of articles in a store. It communicates with a reservations client to attempt reserving a collection of articles for a specific order and store. If successful, it returns a list of reserved articles with their reservation IDs. If any article cannot be reserved, it cancels all reservations made and throws an exception detailing the failure reasons.

## Function Signature
```kotlin
suspend fun reserveArticles(
    orderNumber: OrderNumber,
    articles: Collection<FullServeArticle>,
    storeId: String
): List<ReservedFullServeArticle>
```

## Parameters
- `orderNumber: OrderNumber`: The unique identifier for the order associated with the reservation.
- `articles: Collection<FullServeArticle>`: A collection of `FullServeArticle` objects representing the articles to be reserved.
- `storeId: String`: The identifier of the store where the reservation is to be made.

## Return Value
The function returns a `List<ReservedFullServeArticle>`, where each item in the list represents an article that was successfully reserved, along with its reservation ID.

## Functionality
1. **Create Reservation Requests**: Convert the collection of `FullServeArticle` objects into a list of `ItemReservationRequest` objects, each configured with article details, an availability check, and an expiry date/time set four hours ahead.
2. **Send Reservation Request**: Use the `ReservationsClient` to attempt creating reservations with these requests, passing store details, order number, and token for authentication.
3. **Handle Reservation Results**: Partition the results into reserved and not reserved lists based on reservation status.
   - If any articles are not reserved, cancel all reservations made and throw `NotAllItemsReservedException` with failure reasons.
   - If all articles are reserved, map the results to `ReservedFullServeArticle` objects and return them.
4. **Exception Handling**: Catch `ReservationsClientException`, map error descriptions to failure reasons, and throw `NotAllItemsReservedException`.

## Usage Example
```kotlin
import com.ingka.selling.key.provider.model.OrderNumber
import fullserve.service.FullServeArticle

suspend fun main() {
    val client = // Initialize ReservationsClient
    val tokenHeaderProvider = // Initialize TokenHeaderProvider
    val reservationsFacade = ReservationsFacade(client, tokenHeaderProvider)

    val orderNumber = OrderNumber("12345")
    val articles = listOf(
        FullServeArticle(itemNo = "1001", unitOfMeasure = "pcs", quantity = 2),
        FullServeArticle(itemNo = "1002", unitOfMeasure = "pcs", quantity = 3)
    )
    val storeId = "001"

    try {
        val reservedArticles = reservationsFacade.reserveArticles(orderNumber, articles, storeId)
        reservedArticles.forEach { println("Reserved Article: ${it.article.itemNo}, Reservation ID: ${it.reservationId.value}") }
    } catch (e: NotAllItemsReservedException) {
        println("Reservation failed: ${e.failureReasons}")
    }
}
```

This example demonstrates how to use the `reserveArticles` function to reserve a collection of articles, handling any potential exceptions that indicate failure to reserve all items.
*/
package fullserve.adapters.reservations

import com.ingka.selling.key.provider.model.OrderNumber
import com.ingka.selling.reservations.ReservationsClient
import com.ingka.selling.reservations.exceptions.ReservationsClientException
import com.ingka.selling.reservations.model.CancellationRequest
import com.ingka.selling.reservations.model.ItemCancellationRequest
import com.ingka.selling.reservations.model.ItemReservationRequest
import com.ingka.selling.reservations.model.ReservationFailureReason
import com.ingka.selling.reservations.model.ReservationRequest
import com.ingka.selling.reservations.model.ReservationResult
import com.ingka.selling.reservations.model.ReservationStatus
import com.ingka.selling.service.authentication.TokenHeaderProvider
import fullserve.service.FullServeArticle
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory

const val RESERVATION_TTL_HOURS = 4

@JvmInline
value class ReservationId(val value: String)

data class ReservedFullServeArticle(
    val article: FullServeArticle,
    val reservationId: ReservationId,
)

class ReservationsFacade(private val client: ReservationsClient, private val tokenHeaderProvider: TokenHeaderProvider) {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

    companion object {
        private val logger = LoggerFactory.getLogger(ReservationsFacade::class.java)
    }

    suspend fun reserveArticles(
        orderNumber: OrderNumber,
        articles: Collection<FullServeArticle>,
        storeId: String,
    ): List<ReservedFullServeArticle> {
        val itemReservationRequests =
            articles.map {
                ItemReservationRequest(
                    type = "ART",
                    number = it.itemNo,
                    unitOfMeasure = it.unitOfMeasure,
                    quantity = it.quantity,
                    availabilityCheck = true,
                    expiryDateTime = getReservationExpiration(),
                )
            }
        val reservationRequest = ReservationRequest(itemReservationRequests)

        try {
            val reservationResults = client.createReservations(buType = "STO", buCode = storeId, reference = orderNumber, reservationRequest, tokenHeaderProvider.token())

            val (reserved, notReserved) = reservationResults.partition { it.status == ReservationStatus.RESERVED }

            if (notReserved.isNotEmpty()) {
                cancelReservations(reserved, storeId, orderNumber)

                val errorReasonsMap =
                    notReserved.associate {
                        it.itemNo to (it.errorDescription?.toString() ?: ReservationFailureReason.UNKNOWN_ERROR_CODE.toString())
                    }

                throw NotAllItemsReservedException(
                    cause = null,
                    failureReasons = errorReasonsMap,
                )
            }

            return reservationResults
                .mapNotNull { it.reservationId?.let { id -> it.itemNo to id } }
                .mapNotNull { (itemNo, reservationId) ->
                    articles.find { it.itemNo == itemNo }?.let { article ->
                        ReservedFullServeArticle(
                            article = article,
                            reservationId = ReservationId(reservationId),
                        )
                    }
                }
        } catch (exception: ReservationsClientException) {
            val errorReasonsMap =
                exception.errors.mapValues { (_, error) ->
                    error.description ?: ReservationFailureReason.UNKNOWN_ERROR_CODE.toString()
                }

            throw NotAllItemsReservedException(
                cause = exception,
                failureReasons = errorReasonsMap,
            )
        }
    }

    private suspend fun cancelReservations(
        reserved: List<ReservationResult>,
        storeId: String,
        orderNumber: OrderNumber,
    ) {
        try {
            val cancellationRequest =
                CancellationRequest(
                    reserved.mapNotNull { it.reservationId?.let { id -> ItemCancellationRequest(reservationId = id) } },
                )
            client.cancelReservations(buType = "STO", buCode = storeId, reference = orderNumber, cancellationRequest, tokenHeaderProvider.token())
        } catch (cause: Exception) {
            logger.error("Failed to cancel reservations, ignoring", cause)
        }
    }

    private fun getReservationExpiration() = LocalDateTime.now(ZoneOffset.UTC).plusHours(RESERVATION_TTL_HOURS.toLong()).format(formatter)
}
