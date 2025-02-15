/*
## Overview
The `createOrderRequest` function constructs and returns an `OrderCreationRequest` object based on the provided order details, customer information, and optional delivery arrangements. It utilizes various models and helper functions to transform and organize the input data into a comprehensive order request structure suitable for further processing or submission.

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
    deliveryPrice: SpeCartOrderSubtotalPrice?
): OrderCreationRequest
```

## Parameters
- **items**: `List<Pair<SpeCartItem, ReservationId>>`  
  A list of pairs, each consisting of a shopping cart item (`SpeCartItem`) and its associated reservation ID (`ReservationId`).

- **productTotal**: `SpeCartOrderSubtotalPrice`  
  Represents the subtotal price of the products in the cart, including and excluding savings.

- **summaryPrice**: `SpeCartSummary`  
  Contains the summary of the cart, including total amounts with and without savings.

- **currencyCode**: `String`  
  The currency code (e.g., "USD") used for monetary values in the order.

- **orderNumberId**: `String`  
  A unique identifier for the order number.

- **orderNumberSource**: `String`  
  The source or system from which the order number originates.

- **contactDetails**: `ContactDetails`  
  Contains customer contact information, including email and mobile number.

- **countryCode**: `String`  
  The country code representing the customer's location.

- **languageCode**: `String`  
  The language code used for notifications and communication.

- **storeId**: `String`  
  The identifier for the store fulfilling the order.

- **consumerName**: `String`  
  The name of the consumer or client system initiating the order.

- **selectedTimeWindow**: `CheckoutTimeWindowsResponse?`  
  The selected time window for delivery, if applicable.

- **deliveryArrangementsResponse**: `CheckoutDeliveryArrangementsResponse?`  
  The response containing possible delivery arrangements.

- **checkoutTimeWindowId**: `String?`  
  The identifier for the selected time window during checkout.

- **deliveryPrice**: `SpeCartOrderSubtotalPrice?`  
  The price details related to delivery services, if applicable.

## Return Value
- **OrderCreationRequest**  
  The function returns an `OrderCreationRequest` object, which encapsulates all necessary details for creating an order, including item lines, customer information, payment details, and optional delivery arrangements.

## Functionality
1. **Order Summary Construction**:  
   - Constructs `OrderSummary` using both inclusive and exclusive savings from `productTotal` and `summaryPrice`.
   - Converts subtotal prices and total amounts to `OrderSummaryInclSavings` and `OrderSummaryExclSavings`.

2. **Order Payment Construction**:  
   - If available, constructs `OrderPayment` using inclusive tax price from `productTotal`.

3. **Customer Notifications**:  
   - Builds a `Customer` object including email and SMS notifications derived from `contactDetails`.

4. **Delivery Arrangements**:  
   - Determines if delivery arrangements can be created based on the presence of `deliveryArrangementsResponse`, `selectedTimeWindow`, and `checkoutTimeWindowId`.

5. **Order Creation
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
