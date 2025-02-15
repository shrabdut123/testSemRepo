/*
## Overview

The `createOrderRequest` function constructs an `OrderCreationRequest` object by processing various input parameters related to order details, customer information, and delivery arrangements. This function primarily facilitates the creation of an order request by aggregating and transforming the provided information into a structured format necessary for order processing in a sales system.

## Function Signature

```kotlin
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
): OrderCreationRequest
```

## Parameters

- `items`: `List<Pair<SpeCartItem, ReservationId>>` - A list of pairs, where each pair consists of a shopping cart item and its associated reservation ID.

- `productTotal`: `SpeCartOrderSubtotalPrice` - Contains the subtotal price information of the products in the cart, including savings.

- `summaryPrice`: `SpeCartSummary` - Provides a summary of the cart's pricing details, including total amounts with and without savings.

- `currencyCode`: `String` - The currency code representing the currency used for the transaction.

- `orderNumberId`: `String` - The unique identifier for the order number.

- `orderNumberSource`: `String` - The source of the order number, indicating its origin.

- `contactDetails`: `ContactDetails` - Contains the contact details of the customer, such as email and mobile number.

- `countryCode`: `String` - The ISO country code representing the customer's country.

- `languageCode`: `String` - The language code representing the customer's preferred language.

- `storeId`: `String` - The unique identifier for the store processing the order.

- `consumerName`: `String` - The name of the consumer placing the order.

- `selectedTimeWindow`: `CheckoutTimeWindowsResponse?` - An optional parameter representing the selected delivery time window.

- `deliveryArrangementsResponse`: `CheckoutDeliveryArrangementsResponse?` - An optional parameter containing delivery arrangement details.

- `checkoutTimeWindowId`: `String?` - An optional identifier for the selected checkout time window.

- `deliveryPrice`: `SpeCartOrderSubtotalPrice?` - An optional parameter containing the delivery price information.

## Return Value

The function returns an `OrderCreationRequest` object. This object encapsulates all necessary order details, including item lines, payment information, customer notifications, delivery arrangements, and reservations, structured for order creation and processing.

## Functionality

1. **Order Summary Construction**: 
   - Constructs an `OrderSummary` object by creating both `OrderSummaryInclSavings` and `OrderSummaryExclSavings` using the provided `productTotal` and `summaryPrice`.

2. **Order Payment Setup**:
   - Creates an `OrderPayment` object if the inclusive tax price is available in `productTotal`.

3. **Customer Notifications**:
   - Builds a `Customer` object with notifications based on available contact details (email and mobile number).

4. **Delivery Arrangement Creation**:
   - Conditionally creates a delivery arrangement using the `createDeliveryArrangements` function if all necessary parameters (`deliveryArrangementsResponse`, `selectedTimeWindow`, `checkoutTimeWindowId`) are provided.

5. **Order Request Composition**:
   - Compiles all the constructed objects and parameters into an `OrderCreationRequest` object, including order references, client system details, item lines, and reservations.


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
            savings = 0,
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
