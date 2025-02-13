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
