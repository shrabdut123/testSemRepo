/*
# FullServe Adapters Orders Documentation

This package provides a function to create an order request.

## Import Statements

The package imports various models from the `com.ingka.selling.orders.model` package, which are used to create an order request. It also imports models from `com.ingka.selling.spe.models.pricecart.response` package for handling price cart details. Additionally, it imports several models and functions from `fullserve.adapters.orders.models` and `fullserve.repositories.delivery.models.deliveryarrangements` packages for handling order and delivery details.

## Function: createOrderRequest

This function is used to create an order request. It takes the following parameters:

- `items`: A list of pairs, each containing a `SpeCartItem` and a `ReservationId`.
- `productTotal`: A `SpeCartOrderSubtotalPrice` object representing the total price of the products.
- `summaryPrice`: A `SpeCartSummary` object representing the summary of the price cart.
- `currencyCode`: A string representing the currency code.
- `orderNumberId`: A string representing the order number ID.
- `orderNumberSource`: A string representing the order number source.
- `contactDetails`: A `ContactDetails` object representing the contact details of the customer.
- `countryCode`: A string representing the country code.
- `languageCode`: A string representing the language code.
- `storeId`: A string representing the store ID.
- `consumerName`: A string representing the name of the consumer.
- `selectedTimeWindow`: A `CheckoutTimeWindowsResponse` object representing the selected time window for delivery.
- `deliveryArrangementsResponse`: A `CheckoutDeliveryArrangementsResponse` object representing the delivery arrangements response.
- `checkoutTimeWindowId`: A string representing the checkout time window ID.
- `deliveryPrice`: A `SpeCartOrderSubtotalPrice` object representing the delivery price.

The function returns an `OrderCreationRequest` object.

The function first creates an `OrderSummary` object, which includes savings and total amount details. Then, it creates an `OrderPayment` object, which includes payment details. The function is incomplete and the implementation of the `OrderPayment` object is not shown.
*/
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
            savings = 1.0,
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