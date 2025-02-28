/*
# Documentation

## Function: addNumbers(a, b)

This function takes two numbers as arguments and returns their sum.

### Parameters:

- `a` (Number): The first number to be added.
- `b` (Number): The second number to be added.

### Returns:

- (Number): The sum of `a` and `b`.

### Example:

```js
const num1 = 50;
const num2 = 10;
console.log(`The sum of ${num1} and ${num2} is:`, addNumbers(num1, num2)); // Outputs: The sum of 50 and 10 is: 60
```

## Constants:

- `num1` (Number): A constant that holds the value 50.
- `num2` (Number): A constant that holds the value 10.

## Module Exports:

- `addNumbers`: This function is exported for use in other modules.
*/
function addNumbers(a, b) {
    return a + b;
}

const num1 = 50;
const num2 = 10;
console.log(`The sum of ${num1} and ${num2} is:`, addNumbers(num1, num2));

module.exports = addNumbers;