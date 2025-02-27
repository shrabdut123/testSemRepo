/*
# FullServe Adapters Orders Documentation

This package provides a set of models and functions to handle order-related operations in the FullServe application.

## Imports

The package imports several models from the `com.ingka.selling.orders.model` package, which are used to represent various aspects of an order, such as the customer, delivery arrangements, payment details, and more. It also imports models from the `com.ingka.selling.spe.models.pricecart.response` package for handling price cart responses.

Additionally, it imports several models and functions from the `fullserve.adapters.orders.models` package, which are used to convert between different types of order-related models.

## Functions

### createOrderRequest

This function is used to create an `OrderCreationRequest` object, which represents a request to create a new order.

#### Parameters

- `items`: A list of pairs, where each pair consists of a `SpeCartItem` object (representing an item in the shopping cart) and a `ReservationId` object (representing the reservation ID for the item).
- `productTotal`: A `SpeCartOrderSubtotalPrice` object representing the subtotal price of the order.
- `summaryPrice`: A `SpeCartSummary` object representing the summary price of the order.
- `currencyCode`: A string representing the currency code for the order.
- `orderNumberId`: A string representing the ID of the order number.
- `orderNumberSource`: A string representing the source of the order number.
- `contactDetails`: A `ContactDetails` object representing the contact details for the order.
- `countryCode`: A string representing the country code for the order.
- `languageCode`: A string representing the language code for the order.
- `storeId`: A string representing the ID of the store for the order.
- `consumerName`: A string representing the name of the consumer for the order.
- `selectedTimeWindow`: An optional `CheckoutTimeWindowsResponse` object representing the selected time window for the order.
- `deliveryArrangementsResponse`: An optional `CheckoutDeliveryArrangementsResponse` object representing the delivery arrangements for the order.
- `checkoutTimeWindowId`: An optional string representing the ID of the checkout time window for the order.
- `deliveryPrice`: An optional `SpeCartOrderSubtotalPrice` object representing the delivery price for the order.

#### Returns

An `OrderCreationRequest` object representing the request to create
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
            []
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