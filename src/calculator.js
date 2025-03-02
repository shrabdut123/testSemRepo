/*
# Documentation

## Function: addNumbers

This function takes two numbers as arguments and returns their sum.

### Syntax

```js
addNumbers(a, b)
```

### Parameters

- `a` (Number): The first number to be added.
- `b` (Number): The second number to be added.

### Return Value

This function returns a Number which is the sum of `a` and `b`.

### Example

```js
const num1 = 50;
const num2 = 10;
console.log(`The sum of ${num1} and ${num2} is:`, addNumbers(num1, num2)); // Outputs: The sum of 50 and 10 is: 60
```

This example demonstrates how to use the `addNumbers` function. It defines two numbers, `num1` and `num2`, and then logs a string to the console that includes the sum of `num1` and `num2`.

## Module Exports

This module exports the `addNumbers` function, which means it can be imported and used in other JavaScript files using the `require` function.
*/
function addNumbers(a, b) {
    return a + b;
}

const num1 = 50;
const num2 = 10;
console.log(`The sum of ${num1} and ${num2} is:`, addNumbers(num1, num2));

module.exports = addNumbers;