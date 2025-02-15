/*
## Overview

The `reserveArticles` function is part of the `ReservationsFacade` class in the `fullserve.adapters.reservations` package. It is responsible for reserving a collection of articles for a specific order at a designated store. The function interacts with a reservations client to create reservations, and it handles scenarios where reservations fail by attempting to cancel any successful reservations and throwing a custom exception if not all items are reserved.

## Function Signature

```kotlin
suspend fun reserveArticles(
    orderNumber: OrderNumber,
    articles: Collection<FullServeArticle>,
    storeId: String
): List<ReservedFullServeArticle>
```

## Parameters

- `orderNumber: OrderNumber`  
  A unique identifier representing the order for which reservations are being made.

- `articles: Collection<FullServeArticle>`  
  A collection of `FullServeArticle` objects that need to be reserved.

- `storeId: String`  
  The identifier for the store where the reservations are to be made.

## Return Value

The function returns a `List<ReservedFullServeArticle>`, where each `ReservedFullServeArticle` contains the article that was reserved and its associated reservation ID. This list represents the articles that have been successfully reserved.

## Functionality

1. **Transform Articles to Reservation Requests**:  
   Each article in the provided collection is transformed into an `ItemReservationRequest`, which includes details such as item number, unit of measure, quantity, availability check, and an expiry date/time.

2. **Create Reservation Request**:  
   A `ReservationRequest` object is created using the list of `ItemReservationRequest` objects.

3. **Send Reservation Request**:  
   The function sends the reservation request to the `ReservationsClient`, using the specified store ID, order number, and a token from the `TokenHeaderProvider`.

4. **Handle Reservation Results**:  
   - The results are partitioned into reserved and not reserved items.
   - If some items are not reserved, the function attempts to cancel any reservations that were successful.
   - It then prepares an error map detailing why certain items could not be reserved.

5. **Error Handling**:  
   If not all items are reserved or if a `ReservationsClientException` occurs, a `NotAllItemsReservedException` is thrown with details on the failure reasons.

6. **Return Reserved Articles**:  
   For successfully reserved items, the function constructs and returns a list of `ReservedFullServeArticle` objects.

## Usage Example

```kotlin
import com.ingka.selling.key.provider.model.OrderNumber
import fullserve.service.FullServeArticle

suspend fun main() {
    val reservationsClient = // Initialize ReservationsClient
    val tokenProvider = // Initialize TokenHeaderProvider
    val reservationsFacade = ReservationsFacade(reservationsClient, tokenProvider)

    val orderNumber = OrderNumber("123456789")
    val articles = listOf(
        FullServeArticle(itemNo = "001", unitOfMeasure = "PCS", quantity = 10),
        FullServeArticle(itemNo = "002", unitOfMeasure = "PCS", quantity = 5)
    )
    val storeId = "Store001"

    try {
        val reservedArticles = reservationsFacade.reserveArticles(orderNumber, articles, storeId)
        reservedArticles.forEach {
            println("Reserved Article: ${it.article.itemNo}, Reservation ID: ${it.reservationId.value}")
        }
    } catch (e: NotAllItemsReservedException) {
        println("Failed to reserve all items: ${e.failureReasons}")
    }
}
```

This example demonstrates how to use the `reserveArticles` function to reserve a collection of articles for a specific order at a store, handling any exceptions that may arise if not all items can be reserved.
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
