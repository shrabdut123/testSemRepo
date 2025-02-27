package fullserve.adapters.orders

import com.ingka.selling.orders.model.BusinessUnitKey
import com.ingka.selling.orders.model.ClientSystem
import com.ingka.selling.orders.model.Customer
import com.ingka.selling.orders.model.DeliveryArrangement as OrderDeliveryArrangement
import com.ingka.selling.orders.model.DeliveryItem
import com.ingka.selling.orders.model.DeliveryService
import com.ingka.selling.orders.model.FulfillingUnit
import com.ingka.selling.orders.model.FulfillmentMethod
import com.ingka.selling.orders.model.Notification
import com.ingka.selling.orders.model.OrderCreationRequest
import com.ingka.selling.orders.model.OrderKeyType
import com.ingka.selling.orders.model.OrderPayment
import com.ingka.selling.orders.model.OrderReference
import com.ingka.selling.orders.model.OrderSummary
import com.ingka.selling.orders.model.OrderSummaryExclSavings
import com.ingka.selling.orders.model.OrderSummaryInclSavings
import com.ingka.selling.orders.model.PaymentOnDate
import com.ingka.selling.orders.model.PaymentOnDatePaymentDetails
import com.ingka.selling.orders.model.Reservation as OrderReservation
import com.ingka.selling.orders.model.ReservedItem
import com.ingka.selling.orders.model.SalesOrderKey
import com.ingka.selling.orders.model.Type
import com.ingka.selling.spe.models.pricecart.response.SpeCartItem
import com.ingka.selling.spe.models.pricecart.response.SpeCartOrderSubtotalPrice
import com.ingka.selling.spe.models.pricecart.response.SpeCartSummary
import fullserve.adapters.orders.models.toItemLine
import fullserve.adapters.orders.models.toOrderPickupPoint
import fullserve.adapters.orders.models.toPriceDetails
import fullserve.adapters.orders.models.toSubTotals
import fullserve.adapters.orders.models.toTimeWindow
import fullserve.adapters.orders.models.totalAmount
import fullserve.adapters.reservations.ReservationId
import fullserve.repositories.delivery.models.deliveryarrangements.CheckoutDeliveryArrangementsResponse
import fullserve.repositories.delivery.models.deliveryarrangements.findDeliveryLineByTimeWindowId
import fullserve.repositories.delivery.models.deliveryarrangements.toFullServeDeliveryAssociations
import fullserve.repositories.delivery.models.deliveryarrangements.toFullServeSolution
import fullserve.repositories.delivery.models.timewindows.CheckoutTimeWindowsResponse
import fullserve.service.ContactDetails
import fullserve.service.DeliveryLineNotFoundException
import fullserve.service.DeliveryPriceException
import fullserve.service.PickUpPointNotFoundException
import java.util.UUID

fun createOrderRequest(
    items: List<Pair<SpeCartItem, ReservationId>>,
    productTotal: SpeCartOrderSubtotalPrice,
    summaryPrice: SpeCartSummary,
    currencyCode: String,
    orderNumberId: String,
    orderNumberSource: String,
    contactDetails: ContactDetails,
    countryCode: String,
    languageCode: String,
    storeId: String,
    consumerName: String,
    selectedTimeWindow: CheckoutTimeWindowsResponse?,
    deliveryArrangementsResponse: CheckoutDeliveryArrangementsResponse?,
    checkoutTimeWindowId: String?,
    deliveryPrice: SpeCartOrderSubtotalPrice?,
): OrderCreationRequest {
    val orderSummary =
        OrderSummary(
            inclSavings =
                OrderSummaryInclSavings(
                    subTotals = productTotal.inclSavings.toSubTotals(currencyCode),
                    totalAmount = summaryPrice.inclSavings.totalAmount(currencyCode),
                ),
            exclSavings =
                OrderSummaryExclSavings(
                    subTotals = productTotal.exclSavings.toSubTotals(currencyCode),
                    totalAmount = summaryPrice.exclSavings.totalAmount(currencyCode),
                ),
            savings = 0.0,
            couponsInformation = null,
        )

    val orderPayment =
        productTotal.inclSavings.priceInclTax?.let {
            OrderPayment(
                paymentOnDate =
                    PaymentOnDate(
                        details =
                            PaymentOnDatePaymentDetails(
                                it.toBigDecimal(),
                                currencyCode,
                                null,
                            ),
                    ),
            )
        }

    val customer =
        Customer(
            notifications =
                listOfNotNull(
                    contactDetails.email?.let {
                        Notification(it, "EMAIL", "ISOM_PICKING_READY_FOR_HANDOUT", "$languageCode-$countryCode")
                    },
                    contactDetails.mobileNumber?.let {
                        Notification(it, "SMS", "ISOM_PICKING_READY_FOR_HANDOUT", "$languageCode-$countryCode")
                    },
                ),
        )

    val deliveryArrangement =
        if (deliveryArrangementsResponse != null && selectedTimeWindow != null && checkoutTimeWindowId != null) {
            createDeliveryArrangements(deliveryArrangementsResponse, selectedTimeWindow, deliveryPrice, currencyCode, checkoutTimeWindowId)
        } else {
            null
        }

    return OrderCreationRequest(
        orderKey = SalesOrderKey(orderNumberId, orderNumberSource, OrderKeyType.ISELL),
        businessUnitKey = BusinessUnitKey(storeId, "STO"),
        orderReferences = listOf(OrderReference("", "Checkout-Services")),
        clientSystem = ClientSystem(consumerName, "1.1", null),
        itemLines = items.map { (speItem, reservationId) -> speItem.toItemLine(currencyCode, reservationId) },
        countryCode = countryCode.uppercase(),
        orderCreationDateTime = null,
        orderSummary = orderSummary,
        orderPayment = orderPayment,
        customer = customer,
        orderCreationMethod = "STORE",
        reservations =
            listOf(
                OrderReservation(
                    id = UUID.randomUUID().toString(),
                    reservedItems =
                        items.map { (speItem, reservationId) ->
                            ReservedItem(
                                lineId = speItem.lineId.toInt(),
                                reservationId = reservationId.value,
                            )
                        },
                ),
            ),
        deliveryArrangements = deliveryArrangement?.let { listOf(it) },
    )
}
